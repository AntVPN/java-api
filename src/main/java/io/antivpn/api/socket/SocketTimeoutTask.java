package io.antivpn.api.socket;

import lombok.RequiredArgsConstructor;

import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

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

    private long nextKeepAliveTick = randomKeepAliveDelay();

    private static long randomKeepAliveDelay() {
        return ThreadLocalRandom.current().nextInt(4, 8);
    }

    @Override
    public void run() {
        this.socketManager.getSocketDataHandler().tick();

        if (!this.socketManager.isConnected()) {
            this.socketManager.getSocket().getAntiVPN().getLog().debug("SocketTimeoutTask: socket not connected, triggering reconnect. [tick=%d]", tickCount);
            this.socketManager.reconnect();
            return;
        }

        if (this.socketManager.isPongStale()) {
            this.socketManager.getSocket().getAntiVPN().getLog().error("SocketTimeoutTask: no JSON PONG received in time, connection is stale. Forcing reconnect. [tick=%d]", tickCount);
            this.socketManager.reconnect(true);
            return;
        }

        if (++tickCount >= nextKeepAliveTick) {
            this.socketManager.getSocket().getAntiVPN().getLog().debug("SocketTimeoutTask: sending keepalive [tick=%d]", tickCount);
            this.socketManager.sendKeepAlive();
            nextKeepAliveTick = tickCount + randomKeepAliveDelay();
        }
    }
}
