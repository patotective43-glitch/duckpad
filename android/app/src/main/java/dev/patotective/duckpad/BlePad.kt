package dev.patotective.duckpad

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * El móvil se anuncia por Bluetooth Low Energy como un mando HID (HID over GATT) con la identidad PnP de [Prefs]
 * (por defecto la de DuckPad). Windows lo monta como mando HID genérico sin instalar nada en el PC.
 * El informe sigue el formato BLE de ESP32-BLE-CompositeHID (XboxGamepadDevice); si la identidad hace que
 * Windows cargue otro driver, ese driver escribe informes de salida y la app pasa a esperarlos.
 */
// Solo se arranca con los permisos de Bluetooth ya concedidos, y solo guarda el contexto de la aplicación.
@SuppressLint("MissingPermission", "StaticFieldLeak")
object BlePad {

    enum class State { STARTING, ADVERTISING, CONNECTED, FAILED }

    fun interface Listener {
        fun onBleChanged()
    }

    var state = State.STARTING
        private set
    var failure = ""
        private set
    @Volatile var connectedName: String? = null
        private set

    /** Windows emparejó la entrada clásica «DuckPad» (icono de teléfono) y no la del mando. */
    var wrongEntry = false
        private set

    /** Un PC conecta y se cae al momento varias veces: Windows conserva un vínculo que el móvil ya no tiene. */
    var staleBond = false
        private set

    @Volatile private var lastDeliveredAt = 0L

    /**
     * El PC está suscrito y acepta los informes de entrada. No se espera el informe de salida: el HID genérico no lo
     * envía nunca y el driver de Xbox solo al montar el mando o al pedir vibración, no al reconectar.
     */
    val inputReady get() = sessionOpen && host != null && notifying &&
        lastDeliveredAt != 0L && SystemClock.uptimeMillis() - lastDeliveredAt < 3500
    private var hostClaimedAt = 0L
    /** El PC solo ha vuelto a conectar por el vínculo guardado y aún no ha usado el servicio HID en esta conexión. */
    private var hostTentative = false
    /** Hay un PC enlazado, aunque Windows aún no haya confirmado el mando. */
    val linked get() = host != null

    /**
     * El proceso anterior murió con el PC conectado: Android retiró los servicios en caliente y Windows borra
     * el mando al verlo. Hasta que Windows vuelva a usar el mando, la única salida es emparejar de nuevo.
     */
    @Volatile var hidLost = false
        private set
    val connectionUnconfirmed get() = host != null && !inputReady &&
        hostClaimedAt != 0L && SystemClock.uptimeMillis() - hostClaimedAt > CONFIRM_WAIT_MS

    /** Fuerza 0..100 y duración en ms cuando el PC pide vibración. Se llama en el hilo principal. */
    var onRumble: ((Int, Long) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private lateinit var app: Context
    private var watching = false
    private var server: BluetoothGattServer? = null
    private val pendingServices = ArrayDeque<BluetoothGattService>()
    private lateinit var inputReport: BluetoothGattCharacteristic
    private var users = 0
    private var advertising = false
    private var advertiseCallback: AdvertiseCallback? = null
    private var renamedAt = 0L
    private var advertisingSince = 0L

    /** True mientras la pantalla del mando está abierta. */
    val sessionOpen get() = users > 0

    /** Sin servidor GATT: no queda nada que cerrar. */
    val idle get() = server == null

    @Volatile private var host: BluetoothDevice? = null
    @Volatile private var notifying = false
    private var linkTuner: BluetoothGatt? = null

    // Android avisa de toda conexión LE del móvil (reloj, impresora...), no solo de las del mando: un
    // aparato solo pasa a ser el PC cuando usa el servicio HID o ya lo había usado en otra sesión.
    private val connectedAt = HashMap<String, Long>()
    private val usedHid = HashSet<String>()
    private val freshBonds = HashSet<String>()
    private val quickDrops = HashMap<String, Int>()

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val sentCondition = lock.newCondition()
    private var pending = PadState.NEUTRAL
    private var dirty = false
    private var awaitingSent = false
    @Volatile private var sender: Thread? = null

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    fun acquire(context: Context) {
        app = context.applicationContext
        if (users == 0) {
            wrongEntry = false
            staleBond = false
            quickDrops.clear()
            usedHid.clear()
        }
        users++
        main.removeCallbacks(shutdown)
        main.removeCallbacks(closeServer)
        watchBluetooth()
        when {
            server == null -> start()
            !advertising -> {
                useShortName()
                startAdvertising()
            }
        }
        // Si el PC siguió conectado mientras la pantalla del mando estaba cerrada, se reanuda el envío.
        if (host != null && notifying) {
            lock.withLock { dirty = true }
            startSender()
            setState(State.CONNECTED)
        }
    }

    // Si un PC empareja y en unos segundos no ha conectado por LE, Windows eligió la entrada clásica del móvil.
    // Solo cuenta un ordenador: emparejar unos auriculares durante la sesión no es una entrada equivocada.
    private fun watchBluetooth() {
        if (watching) return
        watching = true
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) return onAdapterState(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1))
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (users == 0 || intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1) != BluetoothDevice.BOND_BONDED) return
                // Un emparejamiento nuevo durante la sesión es el PC que se quiere usar: pasa a preferido al usar el mando.
                freshBonds.add(device.address)
                if (device.bluetoothClass?.majorDeviceClass != BluetoothClass.Device.Major.COMPUTER) return
                main.postDelayed({
                    if (users > 0 && device.address !in connectedAt && device.address !in usedHid &&
                        device.bondState == BluetoothDevice.BOND_BONDED
                    ) {
                        log("${device.address} emparejado sin conexión LE: entrada clásica")
                        // Ese vínculo clásico no sirve para el mando; se olvida al salir como los demás del PC.
                        Prefs(app).let { it.peers = it.peers + device.address }
                        wrongEntry = true
                        setState(state)
                    }
                }, 8000)
            }
        }, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED).apply { addAction(BluetoothAdapter.ACTION_STATE_CHANGED) })
    }

    // Al apagarse el Bluetooth el servidor GATT y el anuncio mueren sin avisar; con la sesión abierta se rehacen al volver.
    private fun onAdapterState(adapterState: Int) {
        when (adapterState) {
            BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                if (server == null) return
                dropServer()
                if (users > 0) fail(app.getString(R.string.ble_fail_off))
            }
            BluetoothAdapter.STATE_ON -> if (users > 0 && server == null) start()
        }
    }

    /** Hay un PC vinculado de otra sesión, el móvil lleva un rato anunciándose y no ha conectado. */
    fun pairedPcSilent(): Boolean {
        if (host != null || !advertising || advertisingSince == 0L) return false
        if (SystemClock.uptimeMillis() - advertisingSince < PAIRED_PC_WAIT_MS) return false
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return Prefs(app).peers.any { adapter.getRemoteDevice(it).bondState == BluetoothDevice.BOND_BONDED }
    }

    /** El mando se perdió en Windows con la última muerte del proceso y el PC no ha vuelto a conectar. */
    fun pcLostController(): Boolean = hidLost && host == null && advertising && advertisingSince != 0L &&
        SystemClock.uptimeMillis() - advertisingSince >= LOST_HINT_MS

    fun release() {
        if (users == 0) return
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) main.postDelayed(shutdown, 1500)
        setState(state)
    }

    /**
     * Decide si [device] pasa a ser el PC del mando. Un PC que ya lo usa no se cambia por otro aparato: un reloj o unos
     * auriculares vinculados también se conectan al móvil y, si le quitan el puesto, Windows deja de recibir informes,
     * se cae y vuelve en bucle. Solo quien usa de verdad el servicio HID sustituye a un PC que únicamente había
     * reconectado por el vínculo guardado. Se llama en el hilo principal.
     */
    private fun claim(device: BluetoothDevice, usedHidNow: Boolean): Boolean {
        val address = device.address
        if (notAHost(device)) {
            log("$address no es un ordenador (clase ${device.bluetoothClass?.majorDeviceClass}): se ignora")
            return false
        }
        if (usedHidNow) usedHid.add(address)
        val prefs = Prefs(app)
        if (usedHidNow && freshBonds.remove(address)) {
            prefs.preferredHost = address
            log("$address acaba de emparejar el mando: es el PC preferido")
        }
        val preferred = prefs.preferredHost
        val current = host
        if (current?.address == address) {
            if (usedHidNow) hostTentative = false
            return true
        }
        val takesOver = usedHidNow && current?.address != preferred && (hostTentative || address == preferred)
        if (current != null && !takesOver) {
            log("$address quiere el mando, pero ya lo usa ${current.address}: se ignora")
            return false
        }
        if (current != null) {
            log("$address usa el servicio HID y sustituye a ${current.address}")
            releaseLinkTuner()
            lastDeliveredAt = 0L
        }
        host = device
        hostTentative = !usedHidNow
        hostClaimedAt = SystemClock.uptimeMillis()
        wrongEntry = false
        staleBond = false
        Prefs(app).let { if (device.address !in it.peers) it.peers = it.peers + device.address }
        connectedName = device.name ?: device.address
        Prefs(app).linkActive = true
        log("${device.address} es el PC del mando")
        setState(State.CONNECTED)
        speedUpLink(device)
        return true
    }

    // Android solo conoce la clase de los aparatos que también usan Bluetooth clásico; un PC solo por LE no tiene.
    private fun notAHost(device: BluetoothDevice) = when (device.bluetoothClass?.majorDeviceClass) {
        BluetoothClass.Device.Major.AUDIO_VIDEO, BluetoothClass.Device.Major.WEARABLE, BluetoothClass.Device.Major.PHONE,
        BluetoothClass.Device.Major.HEALTH, BluetoothClass.Device.Major.TOY, BluetoothClass.Device.Major.IMAGING -> true
        else -> false
    }

    /**
     * Windows deja el enlace del mando a 60 ms entre paquetes (el mando Xbox real va a 7,5 ms). Solo un cliente
     * GATT puede pedir prioridad alta, así que se abre uno sobre el mismo enlace; se repite al acabar Windows su
     * configuración, que vuelve a fijar el intervalo.
     */
    private fun speedUpLink(device: BluetoothDevice) {
        releaseLinkTuner()
        linkTuner = device.connectGatt(app, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState != BluetoothProfile.STATE_CONNECTED) return
                for (delay in longArrayOf(0, 3000, 8000)) {
                    main.postDelayed({
                        if (linkTuner === gatt) log("prioridad alta: ${gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)}")
                    }, delay)
                }
            }
        }, BluetoothDevice.TRANSPORT_LE)
    }

    // El cliente también mantiene vivo el enlace: hay que soltarlo antes de cerrar el servidor.
    private fun releaseLinkTuner() {
        linkTuner?.let {
            it.disconnect()
            it.close()
        }
        linkTuner = null
    }

    fun update(state: PadState) {
        lock.withLock {
            if (state == pending) return
            pending = state
            dirty = true
            changed.signal()
        }
    }

    private fun start() {
        val manager = app.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) return fail(app.getString(R.string.ble_fail_off))
        if (!adapter.isMultipleAdvertisementSupported) return fail(app.getString(R.string.ble_fail_no_advertising))
        val s = manager.openGattServer(app, gattCallback) ?: return fail(app.getString(R.string.ble_fail_gatt))
        useShortName()
        server = s
        val prefs = Prefs(app)
        // Relojes o auriculares que entraron en la lista de PCs con versiones anteriores dejan de contar como PC.
        val notPcs = prefs.peers.filter { notAHost(adapter.getRemoteDevice(it)) }
        if (notPcs.isNotEmpty()) {
            log("fuera de la lista de PCs: $notPcs")
            prefs.peers = prefs.peers - notPcs.toSet()
        }
        if (prefs.linkActive) hidLost = true
        prefs.sessionInterrupted = true
        // La identidad se fija al abrir el servidor; si cambia en ajustes, se aplica en la siguiente sesión.
        val identity = prefs.activeIdentity
        log("identidad ${"AB"[prefs.activeSlot]} $identity")
        setState(State.STARTING)
        pendingServices.clear()
        pendingServices.add(deviceInformationService(identity))
        pendingServices.add(batteryService())
        pendingServices.add(hidService())
        addNextService()
    }

    // Android solo admite añadir un servicio cada vez: el siguiente va en onServiceAdded.
    private fun addNextService() {
        val next = pendingServices.removeFirstOrNull()
        if (next == null) {
            startAdvertising()
            return
        }
        if (server?.addService(next) != true) fail(app.getString(R.string.ble_fail_service, short(next.uuid)))
    }

    private fun startAdvertising() {
        if (users == 0 || advertising) return
        // La respuesta de escaneo lleva el nombre del móvil, y el cambio de nombre tarda en aplicarse.
        val wait = renamedAt + RENAME_MS - SystemClock.uptimeMillis()
        if (wait > 0) {
            main.removeCallbacks(startAdvertisingLater)
            main.postDelayed(startAdvertisingLater, wait)
            return
        }
        val advertiser = app.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser
            ?: return fail(app.getString(R.string.ble_fail_no_advertising))
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        // Swift Pair: Windows enseña un aviso de «Conectar» con un clic al ver este bloque de fabricante
        // (0x0006 = Microsoft; 03 = baliza, 00 = emparejar solo por LE, 80 = byte de RSSI reservado, y
        // el nombre que sale en el aviso). Windows no hace escaneo activo: todo va en el paquete principal,
        // por eso el nombre del móvil se manda en la respuesta de escaneo.
        val swiftPair = byteArrayOf(0x03, 0x00, 0x80.toByte()) + PAD_NAME.toByteArray()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(HID_UUID))
            .addManufacturerData(MICROSOFT_ID, swiftPair)
            .build()
        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).build()
        // Un callback nuevo cada vez: si el Bluetooth se apagó, Android aún guarda el anterior y daría «ya anunciando».
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                if (this !== advertiseCallback) return
                log("anunciando")
                advertisingSince = SystemClock.uptimeMillis()
                if (host == null) {
                    setState(State.ADVERTISING)
                }
            }

            override fun onStartFailure(errorCode: Int) {
                if (this !== advertiseCallback) return
                advertiseCallback = null
                advertising = false
                fail(app.getString(R.string.ble_fail_advertise, errorCode))
            }
        }
        advertiseCallback = callback
        advertising = true
        advertiser.startAdvertising(settings, data, scanResponse, callback)
    }

    private fun stopAdvertising() {
        advertisingSince = 0L
        val callback = advertiseCallback ?: return
        advertiseCallback = null
        advertising = false
        // Con el Bluetooth apagado no hay anunciante, o lanza IllegalStateException; el anuncio ya murió con él.
        try {
            app.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(callback)
        } catch (_: IllegalStateException) {
        }
    }

    /**
     * Fin de sesión: se deja de anunciar, se cierra el servidor (lo único que corta un enlace que abrió el PC)
     * y, con «olvidar PC» activo, se borra el vínculo de cada PC que usó el mando. Windows conserva el mando
     * aunque desaparezcan los servicios: al volver a conectar los lee de nuevo.
     */
    private val shutdown = Runnable {
        if (users > 0) return@Runnable
        stopSender()
        main.removeCallbacks(startAdvertisingLater)
        stopAdvertising()
        // Primero se corta el enlace y luego se retiran los servicios: si desaparecen con el PC conectado,
        // Windows quita el HID del mando y ya no lo espera. El servidor no sostiene el enlace; el cliente sí.
        releaseLinkTuner()
        // Se corta a todo aparato que use o haya usado el mando, no solo al PC actual: otro PC enlazado sin ser el del
        // mando también perdería el HID si los servicios desaparecen con él conectado.
        for (device in hidPeersConnected()) server?.cancelConnection(device)
        main.postDelayed(closeServer, CLOSE_WAIT_MS)
    }

    private fun hidPeersConnected(): List<BluetoothDevice> {
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return listOfNotNull(host)
        val peers = Prefs(app).peers
        return connectedAt.keys.filter { it in usedHid || it in peers || it == host?.address }.map { adapter.getRemoteDevice(it) }
    }

    private val startAdvertisingLater = Runnable { startAdvertising() }

    /**
     * Windows lista el móvil con el nombre que se anuncia, y el móvil ya sale una vez por Bluetooth clásico.
     * Durante la sesión el móvil se llama «DuckPad» para distinguir la entrada del mando; se devuelve el nombre al salir.
     */
    private fun useShortName() {
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val current = adapter.name
        if (current == PAD_NAME) return
        Prefs(app).originalName = current
        if (adapter.setName(PAD_NAME)) renamedAt = SystemClock.uptimeMillis()
    }

    @SuppressLint("MissingPermission")
    fun restoreName(context: Context) {
        val prefs = Prefs(context)
        val original = prefs.originalName ?: return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (adapter.isEnabled) adapter.name = original
        prefs.originalName = null
    }

    private val closeServer = Runnable {
        if (users > 0) return@Runnable
        // Con el PC aún conectado el servidor se queda: se cierra cuando caiga el enlace o se reutiliza en la próxima sesión.
        if (host != null || hidPeersConnected().isNotEmpty()) {
            log("el enlace sigue abierto: el servidor espera")
            return@Runnable
        }
        log("enlace cortado, cerrando el servidor")
        dropServer()
        Prefs(app).sessionInterrupted = false
        restoreName(app)
        setState(State.STARTING)
    }

    /** Fin de sesión inmediato, para cuando la app se cierra desde recientes y no habrá onDestroy. */
    fun endSession() {
        if (!::app.isInitialized) return
        users = 0
        update(PadState.NEUTRAL)
        main.removeCallbacks(shutdown)
        // Give the sender a chance to release controls before dropping the link.
        main.postDelayed(shutdown, 100)
        setState(state)
    }

    private fun dropServer() {
        releaseLinkTuner()
        stopSender()
        main.removeCallbacks(startAdvertisingLater)
        stopAdvertising()
        server?.close()
        server = null
        host = null
        notifying = false
        connectedName = null
        connectedAt.clear()
        lastDeliveredAt = 0L
        hostClaimedAt = 0L
        Prefs(app).linkActive = false
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            log("servicio ${service.uuid} estado=$status")
            main.post {
                // Aviso tardío de un servidor ya cerrado al salir durante el arranque.
                if (server == null) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) addNextService() else fail(app.getString(R.string.ble_fail_service, short(service.uuid)))
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            log("conexión ${device.address} estado=$newState bond=${device.bondState} clase=${device.bluetoothClass?.majorDeviceClass}")
            main.post {
                val address = device.address
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedAt[address] = SystemClock.uptimeMillis()
                    // Otro aparato que conecta mientras el PC usa el mando no reinicia el estado del PC.
                    if (host != null) return@post
                    lastDeliveredAt = 0L
                    // Un PC ya vinculado no vuelve a activar las notificaciones al reconectar: la norma
                    // BLE manda que el servidor recuerde el CCCD de cada cliente vinculado.
                    if (address in Prefs(app).peers && device.bondState == BluetoothDevice.BOND_BONDED && claim(device, false)) {
                        notifying = true
                        lock.withLock { dirty = true }
                        startSender()
                    }
                    return@post
                }
                val since = connectedAt.remove(address)
                if (address == host?.address) {
                    releaseLinkTuner()
                    lastDeliveredAt = 0L
                    host = null
                    notifying = false
                    connectedName = null
                    Prefs(app).linkActive = false
                    stopSender()
                    setState(State.ADVERTISING)
                } else if (since != null && SystemClock.uptimeMillis() - since < QUICK_DROP_MS && address !in usedHid) {
                    // Conectar y caerse sin tocar el mando, una y otra vez, es Windows probando una clave que el móvil borró.
                    val drops = (quickDrops[address] ?: 0) + 1
                    quickDrops[address] = drops
                    if (drops >= QUICK_DROPS_FOR_STALE && users > 0 && !staleBond) {
                        log("$address conecta y se cae: vínculo antiguo en Windows")
                        staleBond = true
                        setState(state)
                    }
                }
                // Con la sesión terminada, el servidor se cierra cuando se va el último aparato que usaba el mando.
                if (users == 0 && server != null) {
                    main.removeCallbacks(closeServer)
                    main.post(closeServer)
                }
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.service?.uuid == HID_UUID) main.post { claim(device, true) }
            val value = when (characteristic.uuid) {
                INPUT_REPORT_UUID -> if (characteristic === inputReport) PadReport.build(lock.withLock { pending }) else ByteArray(8)
                else -> characteristic.value ?: ByteArray(0)
            }
            log("lectura ${short(characteristic.uuid)} offset=$offset")
            respond(device, requestId, offset, value)
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            log("lectura descriptor ${short(descriptor.uuid)} de ${short(descriptor.characteristic.uuid)}")
            respond(device, requestId, offset, descriptor.value ?: ByteArray(0))
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
        ) {
            descriptor.value = value
            log("escritura descriptor ${short(descriptor.uuid)} de ${short(descriptor.characteristic.uuid)} = ${value.joinToString { "%02X".format(it) }}")
            if (descriptor.characteristic?.service?.uuid == HID_UUID) main.post {
                // Solo el PC del mando decide si se envían informes; la suscripción de otro aparato no cuenta.
                if (!claim(device, true) || descriptor.uuid != CCCD_UUID || descriptor.characteristic !== inputReport) return@post
                notifying = value.isNotEmpty() && (value[0].toInt() and 0x01) != 0
                if (notifying) {
                    lock.withLock { dirty = true }
                    startSender()
                } else {
                    stopSender()
                }
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
        ) {
            log("escritura ${short(characteristic.uuid)} = ${value.joinToString { "%02X".format(it) }}")
            val output = characteristic.uuid == INPUT_REPORT_UUID && characteristic !== inputReport && value.size >= 8
            if (characteristic.service?.uuid == HID_UUID) main.post {
                if (!claim(device, true) || !output) return@post
                hidLost = false
                // Informe de salida 3: activadores, gatillo izq, gatillo der, motor grande, motor pequeño
                // (0..100), duración en decenas de ms, retardo y repeticiones.
                val large = value[3].toInt() and 0xFF
                val small = value[4].toInt() and 0xFF
                val ms = (value[5].toInt() and 0xFF) * 10L * ((value[7].toInt() and 0xFF) + 1)
                onRumble?.invoke(maxOf(large, small), ms)
            }
            characteristic.value = value
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            if (device.address == host?.address) {
                val ok = status == BluetoothGatt.GATT_SUCCESS
                lastDeliveredAt = if (ok) SystemClock.uptimeMillis() else 0L
                // Que Windows acepte los informes de entrada demuestra que no ha perdido el mando.
                if (ok) hidLost = false
            }
            lock.withLock {
                awaitingSent = false
                sentCondition.signal()
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            log("MTU $mtu")
        }
    }

    private fun respond(device: BluetoothDevice, requestId: Int, offset: Int, value: ByteArray) {
        // El mapa de informes ocupa más que un paquete: el PC lo pide a trozos con offset.
        val chunk = if (offset >= value.size) ByteArray(0) else value.copyOfRange(offset, value.size)
        server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
    }

    // Se llaman desde el hilo principal y desde los de Binder.
    @Synchronized
    private fun startSender() {
        if (sender != null) return
        // Se guarda antes de arrancar: el bucle sigue mientras sender sea su propio hilo.
        val t = thread(start = false, name = "ble-send", isDaemon = true) { sendLoop() }
        sender = t
        t.start()
        log("envío en marcha")
    }

    @Synchronized
    private fun stopSender() {
        val t = sender ?: return
        sender = null
        log("envío parado")
        t.interrupt()
    }

    private fun sendLoop() {
        val me = Thread.currentThread()
        var sent = 0
        var lastLog = SystemClock.uptimeMillis()
        try {
            while (sender === me) {
                val state = lock.withLock {
                    if (!dirty) changed.await(RESEND_MS, TimeUnit.MILLISECONDS)
                    dirty = false
                    pending
                }
                val device = host ?: continue
                if (!notifying) continue
                val bytes = PadReport.build(state)
                val ok = lock.withLock {
                    awaitingSent = true
                    val accepted = notify(device, bytes)
                    // Android solo admite una notificación en vuelo: se espera a onNotificationSent.
                    if (accepted) sentCondition.await(100, TimeUnit.MILLISECONDS)
                    awaitingSent = false
                    accepted
                }
                if (ok) sent++
                val now = SystemClock.uptimeMillis()
                if (now - lastLog >= 5000) {
                    log("notificaciones enviadas=$sent último=$state")
                    lastLog = now
                }
            }
        } catch (_: InterruptedException) {
        }
    }

    private fun notify(device: BluetoothDevice, bytes: ByteArray): Boolean {
        val s = server ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            s.notifyCharacteristicChanged(device, inputReport, false, bytes) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            inputReport.value = bytes
            @Suppress("DEPRECATION")
            s.notifyCharacteristicChanged(device, inputReport, false)
        }
    }

    private fun deviceInformationService(id: Identity) =
        BluetoothGattService(uuid16(0x180A), BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(readable(0x2A29, PAD_NAME.toByteArray()))
            // PnP ID: origen 02 (VID del USB-IF) y luego VID, PID y versión en little-endian.
            val pnp = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
                .put(0x02).putShort(id.vid.toShort()).putShort(id.pid.toShort()).putShort(id.version.toShort()).array()
            addCharacteristic(readable(0x2A50, pnp))
        }

    private fun batteryService() = BluetoothGattService(uuid16(0x180F), BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
        addCharacteristic(
            BluetoothGattCharacteristic(
                uuid16(0x2A19),
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ).apply {
                value = byteArrayOf(100)
                addDescriptor(cccd())
            },
        )
    }

    private fun hidService() = BluetoothGattService(HID_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
        // HID 1.11, sin país; flags RemoteWake y NormallyConnectable. Sin este último, Windows no
        // vuelve a conectarse solo cuando el mando reaparece.
        addCharacteristic(readable(0x2A4A, byteArrayOf(0x11, 0x01, 0x00, 0x03)))
        addCharacteristic(readable(0x2A4B, PadReport.DESCRIPTOR))
        addCharacteristic(
            BluetoothGattCharacteristic(
                uuid16(0x2A4C),
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            ),
        )
        addCharacteristic(
            BluetoothGattCharacteristic(
                uuid16(0x2A4E),
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
            ).apply { value = byteArrayOf(0x01) },
        )
        // Los informes piden cifrado: así es Windows quien inicia el emparejamiento al leerlos.
        inputReport = BluetoothGattCharacteristic(
            INPUT_REPORT_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
        ).apply {
            addDescriptor(cccd())
            addDescriptor(reportReference(PadReport.INPUT_ID, 0x01))
        }
        addCharacteristic(inputReport)
        addCharacteristic(
            BluetoothGattCharacteristic(
                INPUT_REPORT_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,
            ).apply {
                value = ByteArray(8)
                addDescriptor(reportReference(PadReport.OUTPUT_ID, 0x02))
            },
        )
    }

    private fun readable(id: Int, value: ByteArray) = BluetoothGattCharacteristic(
        uuid16(id), BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ,
    ).apply { this.value = value }

    private fun cccd() = BluetoothGattDescriptor(
        CCCD_UUID,
        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
    ).apply { value = byteArrayOf(0, 0) }

    private fun reportReference(id: Int, type: Int) = BluetoothGattDescriptor(
        uuid16(0x2908), BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED,
    ).apply { value = byteArrayOf(id.toByte(), type.toByte()) }

    private fun fail(reason: String) {
        log("fallo: $reason")
        failure = reason
        setState(State.FAILED)
    }

    private fun setState(s: State) {
        state = s
        main.post { listeners.forEach { it.onBleChanged() } }
    }

    private fun uuid16(id: Int): UUID = UUID.fromString("%08x-0000-1000-8000-00805f9b34fb".format(id))

    private fun short(u: UUID) = u.toString().substring(4, 8)

    private fun log(message: String) {
        Log.i("DuckPadBLE", message)
    }

    private val HID_UUID = uuid16(0x1812)
    private val INPUT_REPORT_UUID = uuid16(0x2A4D)
    private val CCCD_UUID = uuid16(0x2902)
    private const val RESEND_MS = 1000L
    private const val RENAME_MS = 400L
    private const val CLOSE_WAIT_MS = 3000L
    private const val CONFIRM_WAIT_MS = 30000L
    private const val PAIRED_PC_WAIT_MS = 30000L
    private const val LOST_HINT_MS = 8000L
    private const val QUICK_DROP_MS = 1500L
    private const val QUICK_DROPS_FOR_STALE = 3
    private const val MICROSOFT_ID = 0x0006
    private const val PAD_NAME = "DuckPad"
}

/**
 * Informe de entrada de 18 bytes y su descriptor HID: los 16 primeros con la disposición del mando Xbox por BLE y,
 * al final, los gatillos otra vez como Rx y Ry. DirectInput coloca el Acelerador y el Freno en los huecos de Y y Rz,
 * así que con la identidad genérica los gatillos solo llegan a SDL, winmm y Steam por esos dos ejes.
 */
object PadReport {
    const val INPUT_ID = 1
    const val OUTPUT_ID = 3

    val DESCRIPTOR: ByteArray = (
        "05 01 09 05 A1 01 85 01 09 01 A1 00 09 30 09 31 15 00 27 FF FF 00 00 95 02 75 10 81 02 C0 " +
            "09 01 A1 00 09 32 09 35 15 00 27 FF FF 00 00 95 02 75 10 81 02 C0 05 02 09 C5 15 00 26 FF 03 " +
            "95 01 75 0A 81 02 15 00 25 00 75 06 95 01 81 03 05 02 09 C4 15 00 26 FF 03 95 01 75 0A 81 02 " +
            "15 00 25 00 75 06 95 01 81 03 05 01 09 39 15 01 25 08 35 00 46 3B 01 66 14 00 75 04 95 01 81 42 " +
            "75 04 95 01 15 00 25 00 35 00 45 00 65 00 81 03 05 09 19 01 29 0F 15 00 25 01 75 01 95 0F 81 02 " +
            "15 00 25 00 75 01 95 01 81 03 05 0C 0A B2 00 15 00 25 01 95 01 75 01 81 02 15 00 25 00 75 07 " +
            "95 01 81 03 05 01 09 33 09 34 15 00 26 FF 00 75 08 95 02 81 02 " +
            "05 0F 09 21 85 03 A1 02 09 97 15 00 25 01 75 04 95 01 91 02 15 00 25 00 75 04 95 01 " +
            "91 03 09 70 15 00 25 64 75 08 95 04 91 02 09 50 66 01 10 55 0E 15 00 26 FF 00 75 08 95 01 91 02 " +
            "09 A7 15 00 26 FF 00 75 08 95 01 91 02 65 00 55 00 09 7C 15 00 26 FF 00 75 08 95 01 91 02 C0 C0"
        ).split(" ").map { it.toInt(16).toByte() }.toByteArray()

    // Bits de botón del mando Xbox por BLE (no son los de XInput).
    private val BUTTON_MAP = listOf(
        Buttons.A to 0x0001, Buttons.B to 0x0002, Buttons.X to 0x0008, Buttons.Y to 0x0010,
        Buttons.LB to 0x0040, Buttons.RB to 0x0080, Buttons.VIEW to 0x0400, Buttons.MENU to 0x0800,
        Buttons.GUIDE to 0x1000, Buttons.LEFT_THUMB to 0x2000, Buttons.RIGHT_THUMB to 0x4000,
    )

    fun build(s: PadState): ByteArray {
        var buttons = 0
        for ((pad, bit) in BUTTON_MAP) if (s.buttons and pad != 0) buttons = buttons or bit
        // Ejes sin signo; el driver de Xbox invierte Y con 0x7FFF - valor, así que ese es su centro.
        return ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(axis(s.lx)).putShort(invertedAxis(s.ly))
            .putShort(axis(s.rx)).putShort(invertedAxis(s.ry))
            .putShort((s.lt * 1023 / 255).toShort()).putShort((s.rt * 1023 / 255).toShort())
            .put(hat(s.buttons).toByte())
            .putShort(buttons.toShort())
            .put(0)
            .put(s.lt.coerceIn(0, 255).toByte()).put(s.rt.coerceIn(0, 255).toByte())
            .array()
    }

    private fun axis(v: Int): Short = (v.coerceIn(-32768, 32767) + 0x8000).toShort()

    private fun invertedAxis(v: Int): Short = (0x7FFF - v.coerceIn(-32768, 32767)).toShort()

    // 0 = suelta, 1 = norte y en el sentido de las agujas del reloj hasta 8 = noroeste.
    private fun hat(b: Int): Int {
        val up = b and Buttons.DPAD_UP != 0
        val down = b and Buttons.DPAD_DOWN != 0
        val left = b and Buttons.DPAD_LEFT != 0
        val right = b and Buttons.DPAD_RIGHT != 0
        return when {
            up && right -> 2
            right && down -> 4
            down && left -> 6
            left && up -> 8
            up -> 1
            right -> 3
            down -> 5
            left -> 7
            else -> 0
        }
    }
}
