package com.fchen_group.CloudObjectStorageIntegrityChecking.Utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class FileTransferProtocolEncoder extends MessageToByteEncoder<FileTransferProtocol> {

    @Override
    protected void encode(ChannelHandlerContext tcx, FileTransferProtocol msg, ByteBuf out) throws Exception {
        out.writeInt(FileTransferProtocol.magicNumber);

        out.writeInt(msg.op);
        out.writeInt(msg.filenameLength);
        out.writeInt(msg.contentLength);

        out.writeBytes(msg.filename);
        out.writeBytes(msg.content);
    }
}
