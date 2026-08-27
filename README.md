# androidServerManager

> **Instala en 2 pasos en cualquier móvil Android con root. El APK siempre está listo en [Releases](https://github.com/fastreds/androidServerManager/releases).**

App Android nativa (Kotlin + Compose) que **garantiza el server Ubuntu (proot-distro) dentro de Termux** supervisando Tailscale, SSH y PM2 con watchdog.

## Instalación ultra-rápida (cualquier usuario)

**Requisito único:** móvil Android 8+ con root (Magisk / KernelSU / APatch).

1. **Descarga el APK:** abre en el móvil https://github.com/fastreds/androidServerManager/releases/latest → `androidServerManager.apk` y ábrelo para instalar. O desde el PC:
   ```powershell
   adb install -r androidServerManager.apk
   ```
2. **Abre la app y concede root** cuando Magisk lo pida.

¡Listo! La app te guiará si falta algo (Termux, Ubuntu, etc.).

## Primer arranque (si es móvil nuevo)

1. Pestaña **Estado** → verifica el semáforo (Root, Termux, Ubuntu, PM2, Tailscale, SSH).
2. Si algo está en rojo, ve a **Entorno → Analizar entorno** → **Preparar todo (dejar listo para PM2)**. Instala automáticamente lo que falte (proot-distro, Ubuntu ~100 MB, Node, PM2, dump, SSH). *Nota Samsung: si el canal RUN_COMMAND falla, pega en Termux:*
   ```
   mkdir -p ~/.termux
   echo "allow-external-apps = true" >> ~/.termux/termux.properties
   termux-reload-settings
   ```
3. En **Ajustes → Contraseña SSH** escribe la contraseña que usarás para `ssh u0_a305@<IP_Tailscale> -p 8022` (solo se muestra en el dashboard).
4. **Estado → Iniciar watchdog** → supervisión cada 30s con auto-reparación y notificación permanente. Activa también **Eximir de optimización de batería**.

## Actualización automática

En **Ajustes** pon el repo (por defecto `fastreds/androidServerManager`) y activa **Auto-actualizar**. La app comprueba GitHub al abrirse; si hay un tag más nuevo descarga el APK del release y lo instala sola (con root vía `pm install`). Sin root abre el instalador del sistema.

También puedes actualizar manualmente descargando el APK del último Release e instalándolo encima (no borra la configuración).

> El repo siempre tiene el APK listo: cada `git push` compila el APK automáticamente (Actions → Build APK) y cada tag `v*` lo publica en Releases con el APK adjunto.

## Qué hace

- **Root**: shell root persistente con auto-detección de `su` en múltiples rutas.
- **Termux / Ubuntu**: mantiene viva `proot-distro login ubuntu` (`sleep infinity` + tu `start.sh`). Comando editable en Ajustes.
- **Tailscale** (app oficial): detecta IP 100.64.0.0/10; si cae relanza la app.
- **SSH**: auto-detecta 8022/22/2222; arranca `sshd` si hace falta.
- **PM2**: `pm2 jlist` en cada ciclo → altas/bajas automáticas (registra "añadido/eliminado" en Logs). Lista por servicio con estado, reinicios, CPU y RAM. Si daemon cae → `resurrect` + `start all` + `save`.
- **Watchdog** foreground + wake lock, auto-arranque al encender.

## Compilar desde código

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME="C:\Android\Sdk"
gradle assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
O abre la carpeta en Android Studio.

## Notas técnicas

- En Samsung SELinux bloquea `su` a datos de Termux → la app usa el **intent `RUN_COMMAND` de Termux** (`<queries>` + `com.termux.permission.RUN_COMMAND`). Las consultas devuelven salida por socket `bash /dev/tcp` sin permisos de almacenamiento. `su` solo para `id`, `pgrep`, `ip addr`, `am start`, `pkill`.
- Sesión: `nohup proot-distro login ubuntu -- bash -lc '<cmd>' >> ~/server-manager/server.log 2>&1 &`.
- API externa:
  ```bash
  adb shell am broadcast -a com.videototem.servermanager.RUN \
    -n com.videototem.servermanager/.service.CommandReceiver \
    --es mode ubuntu_bg --es b64 <BASE64_DEL_COMANDO>
  # modos: ubuntu_bg | ubuntu_q | raw | raw_q | env_analyze | env_prepare
  ```
