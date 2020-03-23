package com.fchen_group.CloudObjectStorageIntegrityChecking.Run;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class FileTransferProtocolDecoder extends ByteToMessageDecoder {
    /**
     * <pre>
     * 协议开始的标准head_data，int类型，占据4个字节.
     * 表示文件名的长度filenameLength，int类型，占据4个字节.
     * 表示数据的长度contentLength，int类型，占据4个字节.
     * </pre>
     */
    public final int BASE_LENGTH = 4 + 4 + 4;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        // 可读长度必须大于基本长度
        if (buffer.readableBytes() >= BASE_LENGTH) {
            // 记录包头开始的index
            int beginReader;

            while (true) {
                // 获取包头开始的index
                beginReader = buffer.readerIndex();
                // 标记包头开始的index
                buffer.markReaderIndex();
                // 读到了协议的开始标志，结束while循环
                if (buffer.readInt() == FileTransferProtocol.magicNumber) {
                    break;
                }

                // 未读到包头，略过一个字节
                // 每次略过，一个字节，去读取，包头信息的开始标记
                buffer.resetReaderIndex();
                buffer.readByte();

                // 当略过，一个字节之后，
                // 数据包的长度，又变得不满足
                // 此时，应该结束。等待后面的数据到达
                if (buffer.readableBytes() < BASE_LENGTH) {
                    return;
                }
            }

            // 消息的长度
            int op = buffer.readInt();
            int filenameLength = buffer.readInt();
            int contentLength = buffer.readInt();
            // System.out.printf("buffer.readableBytes(): %d\n", buffer.readableBytes());

            // 判断请求数据包数据是否到齐
            if (buffer.readableBytes() < filenameLength + contentLength) {
                // 还原读指针
                buffer.readerIndex(beginReader);
                return;
            }

            // 读取data数据
            byte[] filename = new byte[filenameLength];
            buffer.readBytes(filename);
            byte[] content = new byte[contentLength];
            buffer.readBytes(content);

            FileTransferProtocol protocol = new FileTransferProtocol(op, filename, content);
            out.add(protocol);
        }
    }
}
