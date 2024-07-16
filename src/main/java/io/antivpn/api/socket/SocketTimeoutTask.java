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

    @Override
    public void run() {
        this.socketManager.getSocketDataHandler().tick();

        // Already connected to the socket, so ping the server.
        if (this.socketManager.isConnected()) {
            this.socketManager.sendPing();
            return;
        }

        // Not connected to the socket, so try to reconnect.
        this.socketManager.reconnect();
    }
}
