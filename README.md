# Reproducer demonstrating issue with Netty + BoringSSL wrt SSL renegociation

## With `TLSv1.2` and `SslProvider.OPENSSL`

* Launch WireShark
* Run `Main` class
* Stop WireShark
* filter packets based on address displayed in the logs: `ip.addr == <ADDRESS>

See how connection is crashing.

## With `TLSv1.2` and `SslProvider.JDK`

* Change to `SslProvider.JDK`
* Same as above

Observe that request works fine (400/Bad Request, but that's another story)
