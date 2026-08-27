# androidServerManager

App Android nativa (Kotlin + Compose) que **garantiza la conexión y el funcionamiento del server alojado en Ubuntu (proot-distro) dentro de Termux**, supervisando Tailscale y SSH. Requiere **móvil con root** (Magisk/KernelSU).

## Qué hace

- **Root**: shell root persistente (libsu) para ejecutar binarios de Termux.
- **Termux / proot**: mantiene viva la sesión `proot-distro login ubuntu`. Si existe `/root/start.sh` dentro de Ubuntu lo ejecuta; si no, mantiene la sesión abierta (`sleep infinity`). Comando editable en Ajustes.
- **Tailscale (app oficial)**: detecta la interfaz 100.64.0.0/10 con root; si está caída relanza la app de Tailscale. Recomendado activar **"VPN siempre activa"** en Ajustes del sistema para el paquete `com.tailscale.ipn`.
- **SSH**: auto-detección de puertos (8022 Termux, 22 Ubuntu, 2222). Si no hay ninguno escuchando: arranca `sshd` de Termux (`pkg install openssh`) o el de Ubuntu (`apt install openssh-server`).
- **PM2 (servicios en Ubuntu)**: consulta `pm2 jlist` dentro del Ubuntu de proot **en cada ciclo** → altas/bajas de servicios son automáticas (el watchdog registra "servicio añadido/eliminado" en Logs). La pestaña Estado muestra la lista de servicios con estado individual, nº de reinicios, CPU y RAM. Si el daemon está caído o sin procesos → `pm2 resurrect` + `pm2 start all` + `pm2 save` (el save persiste los servicios añadidos recientemente). Si hay procesos stopped/errored → `pm2 restart <nombre>` individual. Botones "Arreglar PM2" y "Reiniciar todos (PM2)".
- **Health-check opcional**: TCP o HTTP GET configurable (puerto/ruta/timeout) para el server.
- **Watchdog** (servicio foreground + wake lock): ciclo periódico que repara todo automáticamente y notifica el estado. Auto-arranque al encender el móvil (configurable).

## Requisitos en el móvil

1. Root concedido a la app (Magisk → Superusuario).
2. Termux (F-Droid) con: `pkg install proot-distro` y Ubuntu instalado: `proot-distro install ubuntu`.
3. **Canal RUN_COMMAND** (imprescindible, ejecuta comandos en el contexto de Termux — necesario en Samsung donde SELinux bloquea el acceso root a los datos de Termux):
   ```
   mkdir -p ~/.termux
   echo "allow-external-apps = true" >> ~/.termux/termux.properties
   termux-reload-settings
   ```
4. Opcional: `pkg install openssh` (SSH en 8022) o `apt install openssh-server` dentro de Ubuntu.
5. App oficial de Tailscale conectada (idealmente con "VPN siempre activa").
6. Eximir la app de optimización de batería (botón en la pestaña Estado).

## Compilar e instalar

```powershell
# con SDK en C:\Android\Sdk y JBR de Android Studio
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME="C:\Android\Sdk"
C:\Android\gradle-8.7\bin\gradle.bat assembleDebug
C:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

También se puede abrir la carpeta en Android Studio directamente.

## Uso

1. Abrir la app y conceder root.
2. Pestaña **Estado**: revisar componentes (Root, Termux, Ubuntu, Tailscale, SSH, health).
3. **Arrancar** lanza Ubuntu en background; el log queda en `~/server-manager/server.log` dentro de Termux.
4. **Iniciar watchdog** para supervisión continua con auto-recuperación (intervalo configurable en **Ajustes**).

## Notas técnicas

- **Arquitectura**: en Samsung, SELinux impide que root acceda a `/data/data/com.termux`. La app usa el **intent `RUN_COMMAND` de Termux** (con `<queries>` en el manifest y el permiso `com.termux.permission.RUN_COMMAND`) para ejecutar comandos DENTRO del contexto de Termux. Las consultas (p.ej. `pm2 jlist`) devuelven su salida por un socket localhost (`bash /dev/tcp`), sin permisos de almacenamiento. El root (su) solo se usa para: verificar root, `pgrep` (sesión Ubuntu), `ip addr` (Tailscale), `am start` y `pkill`.
- La sesión Ubuntu se lanza con: `nohup proot-distro login ubuntu -- bash -lc '<cmd>' >> ~/server-manager/server.log 2>&1 &` vía RUN_COMMAND.
- Detección de sesión: `pgrep -f 'proot.*ubuntu'` (patrón editable).
- Tailscale se verifica buscando una IPv4 del rango CGNAT (100.64/10) en `ip -o -4 addr`.
- PM2: si el daemon está caído → `pm2 resurrect` + `pm2 start all` + `pm2 save` (en una sesión proot persistente); si hay procesos parados → `pm2 restart <nombre>`.
- Concede a la app el permiso "RUN_COMMAND" cuando la abras por primera vez (o `pm grant com.videototem.servermanager com.termux.permission.RUN_COMMAND`).

## API externa (adb/automatización)

Broadcast explícito (root no requerido para llamador, pero el comando se ejecuta en Termux/Ubuntu):

```bash
# ejecutar dentro de Ubuntu, en background (log en ~/server-manager/server.log)
adb shell am broadcast -a com.videototem.servermanager.RUN \
  -n com.videototem.servermanager/.service.CommandReceiver \
  --es mode ubuntu_bg --es b64 <BASE64_DEL_COMANDO>

# modos: ubuntu_bg | ubuntu_q (espera salida y la registra en Logs) | raw | raw_q
```
