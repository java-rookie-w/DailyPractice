# Netty Lab

> Network programming experiments with Netty — NIO, pipeline architecture, codec design, and protocol implementation.

---

## Module Goals

- Understand Java NIO fundamentals and the Reactor pattern
- Master Netty's Channel → Pipeline → Handler architecture
- Implement custom codecs and protocol framing
- Build HTTP/WebSocket servers and clients
- Explore P2P communication patterns

---

## Learning Path

| # | Experiment | Goal |
|---|-----------|------|
| NET001 | Echo Server | Minimal Netty server + client, understand bootstrap |
| NET002 | Pipeline & Handlers | ChannelInbound/Outbound handler ordering |
| NET003 | Codec Design | ByteToMessageDecoder, MessageToByteEncoder |
| NET004 | Protocol Framing | LengthFieldBasedFrameDecoder, delimiter-based |
| NET005 | HTTP Server | Build a lightweight HTTP server with Netty |
| NET006 | WebSocket Chat | Full-duplex WebSocket communication |
| NET007 | P2P File Transfer | Peer-to-peer file sharing with discovery |

---

## Experiment Standards

- Each experiment demonstrates one concept in isolation
- Server and client code in the same package for easy testing
- Include packet capture (Wireshark) verification notes
- Document threading model and event loop configuration

---

## Resources

- [Netty User Guide](https://netty.io/wiki/user-guide.html)
- [Netty in Action (Manning)](https://www.manning.com/books/netty-in-action)
