package com.fchen_group.CloudObjectStorageIntegrityChecking.Run;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class CoolProtocolEncoder  extends MessageToByteEncoder<CoolProtocol> {

    @Override
    protected void encode(ChannelHandlerContext tcx, CoolProtocol msg, ByteBuf out) throws Exception {
        out.writeInt(CoolProtocol.magicNumber);

        out.writeInt(msg.op);
        out.writeInt(msg.filenameLength);
        out.writeInt(msg.contentLength);

        out.writeBytes(msg.filename);
        out.writeBytes(msg.content);
    }
}
