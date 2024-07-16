package io.antivpn.api.data.socket.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Request {
    private final RequestType type;
}
