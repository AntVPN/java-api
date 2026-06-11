package io.antivpn.api.socket.handler;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.antivpn.api.model.request.CheckRequest;
import io.antivpn.api.model.request.UserDataRequest;
import io.antivpn.api.model.request.UserData;
import io.antivpn.api.model.response.CheckResponse;
import io.antivpn.api.exception.RequestTimeoutException;
import io.antivpn.api.socket.SocketManager;
import io.antivpn.api.util.GsonParser;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class SocketDataHandler {
    public static final RequestTimeoutException requestTimeoutException = new RequestTimeoutException();

    private final Cache<String, CompletableFuture<?>> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(20))
            .removalListener(notification -> {
                CompletableFuture<?> completableFuture = (CompletableFuture<?>) notification.getValue();
                if (completableFuture == null) return;
                completableFuture.completeExceptionally(requestTimeoutException);
            })
            .build();
    private final Cache<String, CompletableFuture<CheckResponse>> checkedCache;

    private final SocketManager socketManager;

    public SocketDataHandler(SocketManager socketManager, Duration cacheDuration) {
        this.socketManager = socketManager;
        this.checkedCache = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheDuration)
                .build();
    }

    /**
     * Verify an IP address using the socket server and return a CompletableFuture with the response.
     * If the player is already being checked, the cached CompletableFuture will be returned.
     * Therefor, if the player is already being checked, the CompletableFuture will be completed when the check is done.
     * If the player is not being checked, a new CompletableFuture will be created and returned.
     *
     * @param checkRequest: The CheckRequest object to send to the socket server.
     * @return: A CompletableFuture with the CheckResponse object.
     */
    public CompletableFuture<CheckResponse> verify(CheckRequest checkRequest) {
        if (!this.socketManager.isConnected()) return null;

        CompletableFuture<CheckResponse> cachedCompletable = checkedCache.getIfPresent(checkRequest.getAddress());
        if (cachedCompletable != null) {
            return cachedCompletable;
        }

        CompletableFuture<CheckResponse> completableFuture = new CompletableFuture<>();
        this.cache.put(checkRequest.getTransactionalId(), completableFuture);
        this.checkedCache.put(checkRequest.getAddress(), completableFuture);
        this.socketManager.getSocket().send(GsonParser.toJson(checkRequest));
        return completableFuture;
    }

    public void sendUserData(UserData userData) {
        if (!this.socketManager.isConnected()) return;

        String json = GsonParser.toJson(
                new UserDataRequest()
                        .sessionId(userData.getSessionId())
                        .username(userData.getUsername())
                        .userId(userData.getUserId())
                        .version(userData.getVersion())
                        .address(userData.getAddress())
                        .server(userData.getServer())
                        .hostname(userData.getHostname())
                        .event(userData.getEvent().name())
                        .premium(userData.isPremium())
        );

        this.socketManager.getSocket().send(json);
    }

    public void handle(CheckResponse checkResponse) {
        CompletableFuture<?> completableFuture = this.cache.getIfPresent(checkResponse.getTransactionalId());
        if (completableFuture == null) return;

        @SuppressWarnings("unchecked")
        CompletableFuture<CheckResponse> checkResponseFuture = (CompletableFuture<CheckResponse>) completableFuture;
        checkResponseFuture.complete(checkResponse);

        cache.invalidate(checkResponse.getTransactionalId());
    }

    public void tick() {
        this.cache.cleanUp();
        this.checkedCache.cleanUp();
    }
}
