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
        }

        // Only send keepalive every ~56s (8s interval × 7 ticks)
        if (++tickCount % 7 == 0) {
            this.socketManager.getSocket().getAntiVPN().getLog().debug("SocketTimeoutTask: sending keepalive [tick=%d]", tickCount);
            this.socketManager.sendKeepAlive();
        }
    }
}
