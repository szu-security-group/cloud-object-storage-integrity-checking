package com.fchen_group.CloudObjectStorageIntegrityChecking.Utils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class FileTransferProtocolDecoder extends ByteToMessageDecoder {
    /**
     * The first 4 bytes of content is operation code
     * Then 4 bytes the length of filename
     * Then 4 bytes the length of the content of file
     * And the rest is the filename and content of file
     *
     * +----------------+----------------+----------------+---------------------------------+
     * +     op code    + filenameLength +  contentLength +     filename and content        +
     * +                +                +                +                                 +
     * +       4B       +       4B       +       4B       +           the rest              +
     * +----------------+----------------+----------------+---------------------------------+
     */

    // if the content of file is null, base length of FileTransferProtocol is 4B * 3
    public final int BASE_LENGTH = 4 + 4 + 4;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        // Readable length must be greater than base length
        if (buffer.readableBytes() >= BASE_LENGTH) {
            // the index of the header
            int beginReader;

            while (true) {
                // get the index of the header
                beginReader = buffer.readerIndex();
                // record the index of the header
                buffer.markReaderIndex();
                // reach the start of the protocol (magic number), end the while loop
                if (buffer.readInt() == FileTransferProtocol.magicNumber) {
                    break;
                }

                // ignore data (try to read magic number)
                buffer.resetReaderIndex();
                buffer.readByte();

                // Wait for subsequent data to arrive
                if (buffer.readableBytes() < BASE_LENGTH) {
                    return;
                }
            }

            int op = buffer.readInt();
            int filenameLength = buffer.readInt();
            int contentLength = buffer.readInt();

            // check whether the requested packet data is completely received
            if (buffer.readableBytes() < filenameLength + contentLength) {
                // reset read index
                buffer.readerIndex(beginReader);
                return;
            }

            // read filename and content
            byte[] filename = new byte[filenameLength];
            buffer.readBytes(filename);
            byte[] content = new byte[contentLength];
            buffer.readBytes(content);

            // restore file and output it
            FileTransferProtocol protocol = new FileTransferProtocol(op, filename, content);
            out.add(protocol);
        }
    }
}
