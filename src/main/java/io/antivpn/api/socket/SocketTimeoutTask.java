package io.antivpn.api.socket;

import lombok.RequiredArgsConstructor;

import java.util.TimerTask;

/**
 * This code has been created by
 * gatogamer#6666 A.K.A. gatogamer.
 * If you want to use my code, please
 * ask first, and give me the credits.
 * Arigato! n.n
 */
@RequiredArgsConstructor
public class SocketTimeoutTask extends TimerTask {
    private final SocketManager socketManager;
    private long tickCount = 0;

    @Override
    public void run() {
        this.socketManager.getSocketDataHandler().tick();

        if (!this.socketManager.isConnected()) {
            this.socketManager.getSocket().getAntiVPN().getLog().debug("SocketTimeoutTask: socket not connected, triggering reconnect. [tick=%d]", tickCount);
            this.socketManager.reconnect();
            return;
        }

        // Detect half-open connections: isConnected()/isOpen() still report true when the TCP link
        // is alive but the server (or Cloudflare) silently stopped responding. Our JSON PONG clock
        // is the only reliable signal, so force a reconnect when it goes stale.
        if (this.socketManager.isPongStale()) {
            this.socketManager.getSocket().getAntiVPN().getLog().error("SocketTimeoutTask: no JSON PONG received in time, connection is stale. Forcing reconnect. [tick=%d]", tickCount);
            this.socketManager.reconnect(true);
            return;
        }

        // Only send keepalive every ~56s (8s interval × 7 ticks)
        if (++tickCount % 7 == 0) {
            this.socketManager.getSocket().getAntiVPN().getLog().debug("SocketTimeoutTask: sending keepalive [tick=%d]", tickCount);
            this.socketManager.sendKeepAlive();
        }
    }
}
