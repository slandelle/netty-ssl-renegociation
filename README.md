# Reproducer demonstrating https://github.com/netty/netty/issues/11505

## With `TLSv1.2` and `SslProvider.OPENSSL`

* Launch WireShark
* Run `Main` class
* Stop WireShark
* filter packets based on address displayed in the logs: `ip.addr == <ADDRESS>

Observe the `ClientHello` and `ServerHello` payloads: the second `ClientHello` always has `Session ID Length: 0` even though the previous `ServerHello` provided a SessionID.

## With `TLSv1.2` and `SslProvider.JDK`

* Change to `SslProvider.JDK`
* Same as above

Observe that the second `ClientHello` as a proper Session ID
