# NIO

- 目录
  - [IO 和 NIO](#IO-和-NIO)
    - [面向流和面向缓冲区](#面向流和面向缓冲区)
    - [Stream 和 Channel](#Stream-和-Channel)
    - [IO 模型](#IO-模型)
    - [零拷贝](#零拷贝)
  - [Java NIO](#Java-NIO)
    - [Buffer](#Buffer)
      - [ByteBuffer](#ByteBuffer)
      - [ByteBuffer 调试工具类](#ByteBuffer-调试工具类)
    - [Channel](#Channel)
      - [图解](#图解)
      - [Java Channel](#Java-Channel)
      - [获取 Channel](#获取-Channel)
  - [Selector](#Selector)
    - [使用](#使用)
      - [创建 Selector](#创建-Selector)
      - [绑定 Channel 事件](#绑定-Channel-事件)
      - [监听 Channel 事件](#监听-Channel-事件)
      - [处理监听事件](#处理监听事件)
  - [网络编程](#网络编程)
    - [问题](#问题)
    - [优化](#优化)
      - [实现思路](#实现思路)
      - [实现代码](#实现代码)

Java NIO（Non-blocking I/O）是从 JDK 1.4 版本开始引入的一个新的 IO API，可以替代标准的 Java IO API。NIO 使用同步非阻塞的方式重写了老的 I/O 了，
即使我们不显式地使用 NIO 方式来编写代码，也能带来性能和速度的提高。这种提升不仅仅体现在文件读写（File I/O），同时也体现在网络读写（Network I/O）中。

NIO 速度的提升来自于使用了更接近操作系统 I/O 执行方式的结构：Channel（通道） 和 Buffer（缓冲区）。我们可以想象一个煤矿：通道就是连接矿层（数据）的矿井，缓冲区是运送煤矿的小车。
通过小车装煤，再从车里取矿。换句话说，我们不能直接和 Channel 交互; 我们需要与 Buffer 交互并将 Buffer 中的数据发送到 Channel 中；Channel 需要从 Buffer 中提取或放入数据。

### IO 和 NIO

传统 IO 是阻塞的、面向流(Stream)的，NIO 是非阻塞的、面向缓冲区(Buffer)的。

#### 面向流和面向缓冲区

- IO

传统 IO 在传输数据时，根据输入输出的不同需要分别建立不同的链接，而且传输的数据是以流的形式在链接上进行传输的。

就像自来水要通过水管将自来水厂和家连接起来一样。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/传统IO示意图.png" width="600px">
</div>

- NIO

NIO 在传输数据时，会在输入输出端之间建立通道，然后将数据放入到缓冲区中。缓冲区通过通道来传输数据。

这里通道就像是铁路，能够连通两个地点。缓冲区就像是火车，能够真正地进行数据的传输。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/NIO示意图.png" width="600px">
</div>

#### Stream 和 Channel

- Stream 不会自动缓冲数据，Channel 会利用系统提供的发送缓冲区、接收缓冲区（更为底层）。
- Stream 仅支持阻塞 API，Channel 同时支持阻塞、非阻塞 API，网络 Channel 可配合 Selector 实现多路复用。
- 二者均为全双工，即读写可以同时进行。

#### IO 模型

请点击 [IO 模型](https://github.com/lazecoding/Note/blob/main/note/articles/network/I0Model.md#IO-模型) 了解相关内容。

#### 零拷贝

##### 传统 IO 问题

传统的 IO 将一个文件通过 socket 写出：

```java
File f = new File("helloword/data.txt");
RandomAccessFile file = new RandomAccessFile(file, "r");

byte[] buf = new byte[(int)f.length()];
file.read(buf);

Socket socket = ...;
socket.getOutputStream().write(buf);
```

内部工作流程：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/传统IO数据拷贝.png" width="600px">
</div>

1. java 本身并不具备 IO 读写能力，因此 read 方法调用后，要从 java 程序的**用户态**切换至**内核态**，去调用操作系统的读能力，将数据读入**内核缓冲区**。
这期间用户线程阻塞，操作系统使用 DMA（Direct Memory Access）来实现文件读，其间也不会使用 CPU。
   > DMA 也可以理解为硬件单元，用来解放 CPU 完成文件 IO。
3. 从**内核态**切换回**用户态**，将数据从**内核缓冲区**读入**用户缓冲区**（即 byte[] buf），这期间 CPU 会参与拷贝，无法利用 DMA。
4. 调用 write 方法，这时将数据从**用户缓冲区**（byte[] buf）写入 **socket 缓冲区**，CPU 会参与拷贝。
5. 接下来要向网卡写数据，这项能力 java 又不具备，因此又得从**用户态**切换至**内核态**，调用操作系统的写能力，使用 DMA 将 **socket 缓冲区**的数据写入网卡，不会使用 CPU。

可以看到中间环节较多，java 的 IO 实际不是物理设备级别的读写，而是缓存的复制，底层的真正读写是操作系统来完成的。

* 用户态与内核态的切换发生了 3 次，这个操作比较重量级。
* 数据拷贝了共 4 次。

#### NIO 优化

通过 DirectByteBuf：

* ByteBuffer.allocate(10)  HeapByteBuffer 使用的还是 java 内存。
* ByteBuffer.allocateDirect(10)  DirectByteBuffer 使用的是操作系统内存。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/NIO数据拷贝1.png" width="600px">
</div>

大部分步骤与优化前相同，不再赘述。唯有一点：java 可以使用 DirectByteBuf 将堆外内存映射到 jvm 内存中来直接访问使用。

* 这块内存不受 jvm 垃圾回收的影响，因此内存地址固定，有助于 IO 读写。
* java 中的 DirectByteBuf 对象仅维护了此内存的虚引用，内存回收分成两步。
  * DirectByteBuf 对象被垃圾回收，将虚引用加入引用队列。
  * 通过专门线程访问引用队列，根据虚引用释放堆外内存。
* 减少了一次数据拷贝，用户态与内核态的切换次数没有减少。

`进一步优化（底层采用了 linux 2.1 后提供的 sendFile 方法`），java 中对应着两个 channel 调用 transferTo/transferFrom 方法拷贝数据。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/NIO数据拷贝2.png" width="600px">
</div>

1. java 调用 transferTo 方法后，要从 java 程序的**用户态**切换至**内核态**，使用 DMA将数据读入**内核缓冲区**，不会使用 CPU。
2. 数据从**内核缓冲区**传输到 **socket 缓冲区**，CPU 会参与拷贝。
3. 最后使用 DMA 将 **socket 缓冲区**的数据写入网卡，不会使用 CPU。

可以看到：

* 只发生了一次用户态与内核态的切换。
* 数据拷贝了 3 次。

`进一步优化（linux 2.4）`：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/NIO数据拷贝3.png" width="600px">
</div>

1. java 调用 transferTo 方法后，要从 java 程序的**用户态**切换至**内核态**，使用 DMA将数据读入**内核缓冲区**，不会使用 CPU。
2. 只会将一些 offset 和 length 信息拷入 **socket 缓冲区**，几乎无消耗。
3. 使用 DMA 将 **内核缓冲区**的数据写入网卡，不会使用 CPU。

整个过程仅只发生了一次用户态与内核态的切换，数据拷贝了 2 次。所谓的 **零拷贝**，并不是真正无拷贝，而是在不会拷贝重复数据到 jvm 内存中，零拷贝的优点：

* 更少的用户态与内核态的切换。
* 不利用 CPU 计算，减少 CPU 缓存伪共享。
* 零拷贝适合小文件传输。

### Java NIO

Java NIO 系统的核心在于：通道(Channel)和缓冲区(Buffer)。通道表示打开到 IO 设备(例如：文件、套接字)的连接。若需要使用 NIO 系统，
需要获取用于连接 IO 设备的通道以及用于容纳数据的缓冲区，然后操作缓冲区对数据进行处理。

简而言之，通道负责传输，缓冲区负责存储。

#### Buffer

Buffer （缓冲区）就像一个数组，可以保存多个相同类型的数据。Buffer有以下几种，其中使用较多的是 ByteBuffer。

- ByteBuffer
    - MappedByteBuffer
    - DirectByteBuffer
    - HeapByteBuffer
- ShortBuffer
- IntBuffer
- LongBuffer
- FloatBuffer
- DoubleBuffer
- CharBuffer

Buffer 是所有缓冲区父类，它是一个抽象类，它的几个核心属性如下：

```java
// 必须满足: mark <= position <= limit <= capacity
private int mark = -1;
private int position = 0;
private int limit;
private int capacity;
```

- capacity：缓冲区的容量。通过构造函数赋予，一旦设置，无法更改。
- limit：缓冲区的界限。位于 limit  后的数据不可读写。缓冲区的限制不能为负，并且不能大于其容量。
- position：下一个读写位置的索引。缓冲区的位置不能为负，并且不能大于 limit。
- mark：记录当前 position 的值。position 被改变后，可以通过调用 reset() 方法恢复到 mark 的位置。

以上四个属性必须满足：`mark <= position <= limit <= capacity`

核心方法：

- put

put() 方法可以将一个数据放入到缓冲区中；进行该操作后，postition 的值会 +1，指向下一个可以放入的位置。capacity = limit ，为缓冲区容量的值。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/put方法.png" width="600px">
</div>

- flip

flip() 方法会切换对缓冲区的操作模式，由写->读 / 读->写。

进行该操作后：
如果是`写->读`模式，position = 0 ， limit 指向最后一个元素的下一个位置，capacity不变；
如果是`读->写`，则恢复为 put() 方法中的值。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/flip方法.png" width="600px">
</div>

- get

get() 方法会读取缓冲区中的一个值，进行该操作后，position 会 +1，如果超过了 limit 则会抛出异常。

注意：get(i)方法不会改变 position 的值。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/get方法.png" width="600px">
</div>

- rewind

rewind() 方法后，会恢复 position、limit 和 capacity 的值，变为进行 get() 前的值。

注意：该方法只能在读模式下使用。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/rewind方法.png" width="600px">
</div>

- clear

clear() 方法会将缓冲区中的各个属性恢复为最初的状态，`position = 0, capacity = limit`。
此时缓冲区的数据依然存在，处于 "被遗忘" 状态，下次进行写操作时会覆盖这些数据。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/clear方法.png" width="600px">
</div>

- mark 和 reset

mark()方法会将postion的值保存到mark属性中；
reset()方法会将position的值改为mark中保存的值。

- compact

compact 会把未读完的数据向前压缩，然后切换到写模式。
数据前移后，原位置的值并未清零，写时会覆盖之前的值。

注意：此方法为 ByteBuffer 的方法，而不是 Buffer 的方法。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/compact方法.png" width="600px">
</div>

- clear 对比 compact

clear 只是对 position、limit、mark 进行重置，而 compact 在对 position 进行设置，以及 limit、mark 进行重置的同时，还涉及到数据在内存中拷贝（会调用 arraycopy）。
所以 compact 比 clear 更耗性能。但 compact 能保存你未读取的数据，将新数据追加到为读取的数据之后；而 clear 则不行，若你调用了 clear，则未读取的数据就无法再读取到了。

##### ByteBuffer

有且仅有 ByteBuffer（字节缓冲区，保存原始字节的缓冲区）这一类型可直接与通道交互。查看 `java.nio.ByteBuffer` 的 JDK 文档，
你会发现它是相当基础的：通过初始化某个大小的存储空间，再使用一些方法以原始字节形式或原始数据类型来放置和获取数据。但是我们无法直接存放对象，即使是最基本的 String 类型数据。
这是一个相当底层的操作，也正因如此，使得它与大多数操作系统的映射更加高效。

ByteBuffer 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/ByteBuffer类图.png" width="400px">
</div>

ByteBuffer 是 Buffer 的子类，而且它还有 MappedByteBuffer、DirectByteBuffer 和 HeapByteBuffer 几个子类。

核心方法：

- 继承 Buffer 方法，不走赘述。

- allocate

通过 allocate() 方法获取的缓冲区都是非直接缓冲区，这些缓冲区是建立在 JVM 堆内存之中的。

```java
public static ByteBuffer allocate(int capacity) {
    if (capacity < 0)
        throw new IllegalArgumentException();
    return new HeapByteBuffer(capacity, capacity);
}
```

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/非直接缓冲区.png" width="600px">
</div>

通过非直接缓冲区，想要将数据写入到物理磁盘中，或者是从物理磁盘读取数据。都需要经过 JVM 和操作系统，数据在两个地址空间中传输时，会 copy 一份保存在对方的空间中。
所以费直接缓冲区的读取效率较低。

- allocateDirect

通过 allocateDirect() 获取的缓冲区为直接缓冲区，这些缓冲区是建立在物理内存之中的。

```java
public static ByteBuffer allocateDirect(int capacity) {
    return new DirectByteBuffer(capacity);
}
```

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/直接缓冲区.png" width="600px">
</div>

直接缓冲区通过在操作系统和 JVM 之间创建物理内存映射文件加快缓冲区数据读/写入物理磁盘的速度。放到物理内存映射文件中的数据就不归应用程序控制了，
操作系统会自动将物理内存映射文件中的数据写入到物理内存中。

- 向 buffer 写入数据

调用 channel 的 read 方法；调用 buffer 自己的 put 方法。

```java
// 调用 channel 的 read 方法；
int readBytes = channel.read(buf);
// 调用 buffer 自己的 put 方法。
buf.put((byte)127);
```

- 从 buffer 读取数据

调用 channel 的 write 方法；调用 buffer 自己的 get 方法。

```java
// 调用 channel 的 write 方法
int writeBytes = channel.write(buf);
// 调用 buffer 自己的 get 方法
byte b = buf.get();
```

##### ByteBuffer 调试工具类

前置依赖：

```C
<dependency>
  <groupId>io.netty</groupId>
  <artifactId>netty-all</artifactId>
  <version>4.1.51.Final</version>
</dependency>
```

工具类源码：

```java
public class ByteBufferUtil {
    private static final char[] BYTE2CHAR = new char[256];
    private static final char[] HEXDUMP_TABLE = new char[256 * 4];
    private static final String[] HEXPADDING = new String[16];
    private static final String[] HEXDUMP_ROWPREFIXES = new String[65536 >>> 4];
    private static final String[] BYTE2HEX = new String[256];
    private static final String[] BYTEPADDING = new String[16];

    static {
        final char[] DIGITS = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 256; i++) {
            HEXDUMP_TABLE[i << 1] = DIGITS[i >>> 4 & 0x0F];
            HEXDUMP_TABLE[(i << 1) + 1] = DIGITS[i & 0x0F];
        }

        int i;

        // Generate the lookup table for hex dump paddings
        for (i = 0; i < HEXPADDING.length; i++) {
            int padding = HEXPADDING.length - i;
            StringBuilder buf = new StringBuilder(padding * 3);
            for (int j = 0; j < padding; j++) {
                buf.append("   ");
            }
            HEXPADDING[i] = buf.toString();
        }

        // Generate the lookup table for the start-offset header in each row (up to 64KiB).
        for (i = 0; i < HEXDUMP_ROWPREFIXES.length; i++) {
            StringBuilder buf = new StringBuilder(12);
            buf.append(StringUtil.NEWLINE);
            buf.append(Long.toHexString(i << 4 & 0xFFFFFFFFL | 0x100000000L));
            buf.setCharAt(buf.length() - 9, '|');
            buf.append('|');
            HEXDUMP_ROWPREFIXES[i] = buf.toString();
        }

        // Generate the lookup table for byte-to-hex-dump conversion
        for (i = 0; i < BYTE2HEX.length; i++) {
            BYTE2HEX[i] = ' ' + StringUtil.byteToHexStringPadded(i);
        }

        // Generate the lookup table for byte dump paddings
        for (i = 0; i < BYTEPADDING.length; i++) {
            int padding = BYTEPADDING.length - i;
            StringBuilder buf = new StringBuilder(padding);
            for (int j = 0; j < padding; j++) {
                buf.append(' ');
            }
            BYTEPADDING[i] = buf.toString();
        }

        // Generate the lookup table for byte-to-char conversion
        for (i = 0; i < BYTE2CHAR.length; i++) {
            if (i <= 0x1f || i >= 0x7f) {
                BYTE2CHAR[i] = '.';
            } else {
                BYTE2CHAR[i] = (char) i;
            }
        }
    }

    /**
     * 打印所有内容
     *
     * @param buffer
     */
    public static void debugAll(ByteBuffer buffer) {
        int oldlimit = buffer.limit();
        buffer.limit(buffer.capacity());
        StringBuilder origin = new StringBuilder(256);
        appendPrettyHexDump(origin, buffer, 0, buffer.capacity());
        System.out.println("+--------+-------------------- all ------------------------+----------------+");
        System.out.printf("position: [%d], limit: [%d]\n", buffer.position(), oldlimit);
        System.out.println(origin);
        buffer.limit(oldlimit);
    }

    /**
     * 打印可读取内容
     *
     * @param buffer
     */
    public static void debugRead(ByteBuffer buffer) {
        StringBuilder builder = new StringBuilder(256);
        appendPrettyHexDump(builder, buffer, buffer.position(), buffer.limit() - buffer.position());
        System.out.println("+--------+-------------------- read -----------------------+----------------+");
        System.out.printf("position: [%d], limit: [%d]\n", buffer.position(), buffer.limit());
        System.out.println(builder);
    }

    private static void appendPrettyHexDump(StringBuilder dump, ByteBuffer buf, int offset, int length) {
        if (MathUtil.isOutOfBounds(offset, length, buf.capacity())) {
            throw new IndexOutOfBoundsException(
                    "expected: " + "0 <= offset(" + offset + ") <= offset + length(" + length
                            + ") <= " + "buf.capacity(" + buf.capacity() + ')');
        }
        if (length == 0) {
            return;
        }
        dump.append(
                "         +-------------------------------------------------+" +
                        StringUtil.NEWLINE + "         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |" +
                        StringUtil.NEWLINE + "+--------+-------------------------------------------------+----------------+");

        final int startIndex = offset;
        final int fullRows = length >>> 4;
        final int remainder = length & 0xF;

        // Dump the rows which have 16 bytes.
        for (int row = 0; row < fullRows; row++) {
            int rowStartIndex = (row << 4) + startIndex;

            // Per-row prefix.
            appendHexDumpRowPrefix(dump, row, rowStartIndex);

            // Hex dump
            int rowEndIndex = rowStartIndex + 16;
            for (int j = rowStartIndex; j < rowEndIndex; j++) {
                dump.append(BYTE2HEX[getUnsignedByte(buf, j)]);
            }
            dump.append(" |");

            // ASCII dump
            for (int j = rowStartIndex; j < rowEndIndex; j++) {
                dump.append(BYTE2CHAR[getUnsignedByte(buf, j)]);
            }
            dump.append('|');
        }

        // Dump the last row which has less than 16 bytes.
        if (remainder != 0) {
            int rowStartIndex = (fullRows << 4) + startIndex;
            appendHexDumpRowPrefix(dump, fullRows, rowStartIndex);

            // Hex dump
            int rowEndIndex = rowStartIndex + remainder;
            for (int j = rowStartIndex; j < rowEndIndex; j++) {
                dump.append(BYTE2HEX[getUnsignedByte(buf, j)]);
            }
            dump.append(HEXPADDING[remainder]);
            dump.append(" |");

            // Ascii dump
            for (int j = rowStartIndex; j < rowEndIndex; j++) {
                dump.append(BYTE2CHAR[getUnsignedByte(buf, j)]);
            }
            dump.append(BYTEPADDING[remainder]);
            dump.append('|');
        }

        dump.append(StringUtil.NEWLINE +
                "+--------+-------------------------------------------------+----------------+");
    }

    private static void appendHexDumpRowPrefix(StringBuilder dump, int row, int rowStartIndex) {
        if (row < HEXDUMP_ROWPREFIXES.length) {
            dump.append(HEXDUMP_ROWPREFIXES[row]);
        } else {
            dump.append(StringUtil.NEWLINE);
            dump.append(Long.toHexString(rowStartIndex & 0xFFFFFFFFL | 0x100000000L));
            dump.setCharAt(dump.length() - 9, '|');
            dump.append('|');
        }
    }

    public static short getUnsignedByte(ByteBuffer buffer, int index) {
        return (short) (buffer.get(index) & 0xFF);
    }
}
```

使用：

```java
public class TestByteBuffer {
    public static void main(String[] args) {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        // 向buffer中写入1个字节的数据
        buffer.put((byte)97);
        // 使用工具类，查看buffer状态
        ByteBufferUtil.debugAll(buffer);

        // 向buffer中写入4个字节的数据
        buffer.put(new byte[]{98, 99, 100, 101});
        ByteBufferUtil.debugAll(buffer);

        // 获取数据
        buffer.flip();
        ByteBufferUtil.debugAll(buffer);
        System.out.println(buffer.get());
        System.out.println(buffer.get());
        ByteBufferUtil.debugAll(buffer);

        // 使用compact切换模式
        buffer.compact();
        ByteBufferUtil.debugAll(buffer);

        // 再次写入
        buffer.put((byte)102);
        buffer.put((byte)103);
        ByteBufferUtil.debugAll(buffer);
    }
}
```

结果：

```C
// 向缓冲区写入了一个字节的数据，此时postition为1
+--------+-------------------- all ------------------------+----------------+
position: [1], limit: [10]
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 61 00 00 00 00 00 00 00 00 00                   |a.........      |
+--------+-------------------------------------------------+----------------+

// 向缓冲区写入四个字节的数据，此时position为5
+--------+-------------------- all ------------------------+----------------+
position: [5], limit: [10]
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 61 62 63 64 65 00 00 00 00 00                   |abcde.....      |
+--------+-------------------------------------------------+----------------+

// 调用flip切换模式，此时position为0，表示从第0个数据开始读取
+--------+-------------------- all ------------------------+----------------+
position: [0], limit: [5]
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 61 62 63 64 65 00 00 00 00 00                   |abcde.....      |
+--------+-------------------------------------------------+----------------+
// 读取两个字节的数据             
97
98
            
// position变为2             
+--------+-------------------- all ------------------------+----------------+
position: [2], limit: [5]
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 61 62 63 64 65 00 00 00 00 00                   |abcde.....      |
+--------+-------------------------------------------------+----------------+
             
// 调用compact切换模式，此时position及其后面的数据被压缩到ByteBuffer前面去了
// 此时position为3，会覆盖之前的数据             
+--------+-------------------- all ------------------------+----------------+
position: [3], limit: [10]
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 63 64 65 64 65 00 00 00 00 00                   |cdede.....      |
+--------+-------------------------------------------------+----------------+
             
// 再次写入两个字节的数据，之前的 0x64 0x65 被覆盖         
+--------+-------------------- all ------------------------+----------------+
position: [5], limit: [10]
         +-------------------------------------------------+
         |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
+--------+-------------------------------------------------+----------------+
|00000000| 63 64 65 66 67 00 00 00 00 00                   |cdefg.....      |
+--------+-------------------------------------------------+----------------+
```

#### Channel

Channel 由 `java.nio.channels` 包定义的。Channel 表示 IO 源与目标打开的连接。Channel 类似于传统的 "流"。只不过 Channel 本身不能直接访问数据，Channel 只能与 Buffer 进行交互。

常见的 Channel 有以下四种，其中 FileChannel 主要用于文件传输，其余三种用于网络通信。

- FileChannel
- DatagramChannel
- SocketChannel
- ServerSocketChannel

##### 图解

应用程序进行读写操作调用函数时，`底层调用的操作系统提供给用户的读写 API`，调用这些 API 时会生成对应的指令，CPU 则会执行这些指令。在计算机刚出现的那段时间，
`所有读写请求的指令都有 CPU 去执行`，过多的读写请求会导致 CPU 无法去执行其他命令，从而 CPU 的利用率降低。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/CPU操作IO.png" width="600px">
</div>


后来，DMA(Direct Memory Access，直接存储器访问)出现了。当 IO 请求传到计算机底层时，`DMA 会向 CPU 请求，让 DMA 去处理这些 IO 操作`，从而可以让 CPU 去执行其他指令。
DMA 处理 IO 操作时，会请求获取总线的使用权。`当IO请求过多时，会导致大量总线用于处理IO请求，从而降低效率`。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/DMA操作IO.png" width="600px">
</div>

于是便有了 `Channel(通道)`，Channel 相当于一个`专门用于 IO 操作的独立处理器`，它具有独立处理 IO 请求的能力，当有 IO 请求时，它会自行处理这些 IO 请求。

##### Java Channel

Channel 类图：

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/Channel类图.png" width="600px">
</div>

-本地文件 IO
- FileChannel
- 网络 IO
    - SocketChanel、ServerSocketChannel：用于 TCP 传输
    - DatagramChannel：用于 UDP 传输

##### 获取 Channel

获取 Channel 的一种方式是对支持通道的对象调用 `getChannel()` 方法。支持通道的类如下：

- FileInputStream
- FileOutputStream
- RandomAccessFile
- DatagramSocket
- Socket
- ServerSocket

此外还可以通过通道的静态方法 `open()` 来获取 Channel。

### Selector

Selector 是多路复用器选择器，它允许单线程处理多个 Channel。

使用 Selector，首先得向 Selector 注册 Channel，然后调用它的 select()。该方法会一直阻塞，直到某个注册的 Channel 有事件就绪。一旦这个方法返回，线程就可以处理这些事件，
事件的例子如建立新连接，数据接收等。

<div align="left">
    <img src="https://github.com/lazecoding/Note/blob/main/images/java/nio/Selector模型.png" width="600px">
</div>

单线程可以配合 Selector 完成对多个 Channel 可读写事件的监控，这被称为`多路复用`。

- 多路复用仅针对网络 IO、普通文件 IO 没法利用多路复用。
- 如果不用 Selector 的非阻塞模式，线程大部分时间都在做无用功，而 Selector 能够保证：
`有可连接事件时才去连接、有可读事件才去读取、有可写事件才去写入`。

多路复用的优点：

- 一个线程配合 Selector 就可以监控多个 channel 的事件，事件发生线程才去处理，避免非阻塞模式下做的无用功。
- 让这个线程能够被充分利用。
- 节约了线程的数量。
- 减少了线程上下文切换。

#### 使用

##### 创建 Selector

```java
Selector selector = Selector.open();
```

##### 绑定 Channel 事件

`绑定 Channel 事件`也称之为注册事件，Selector 只关心绑定的事件。
每个 Channel 向 Selector 注册时,都将会创建一个 SelectionKey，它将 Channel 与 Selector 建立关系,并维护了 Channel 事件.

```java
channel.configureBlocking(false);
SelectionKey key = channel.register(selector, 绑定事件);
```

注意：

- Channel 必须工作在非阻塞模式。
- FileChannel 没有非阻塞模式，因此不能配合 Selector 一起使用。
- 绑定的事件类型可以有：
  - connect：客户端连接成功时触发。
  - accept：服务器端成功接受连接时触发。
  - read：数据可读入时触发，有因为接收能力弱，数据暂不能读入的情况。
  - write：数据可写出时触发，有因为发送能力弱，数据暂不能写出的情况。

##### 监听 Channel 事件

可以通过下面三种方法来监听是否有事件发生，方法的返回值代表有多少 Channel 发生了事件

方法1，阻塞直到绑定事件发生

```java
int count = selector.select();
```

方法2，阻塞直到绑定事件发生，或是超时（时间单位为 ms）

```java
int count = selector.select(long timeout);
```

方法3，不会阻塞，也就是不管有没有事件，立刻返回，自己根据返回值检查是否有事件

```java
int count = selector.selectNow();
```

##### 处理监听事件

事件发生后，要么处理，要么取消（cancel），不能什么都不做，否则下次该事件仍会触发，这是因为 nio 底层使用的是水平触发。

```java
public static void main(String[] args) throws IOException {
    // 1. 创建 selector, 管理多个 channel
    Selector selector = Selector.open();
    ServerSocketChannel ssc = ServerSocketChannel.open();
    ssc.configureBlocking(false);
    // 2. 建立 selector 和 channel 的联系（注册）
    // SelectionKey 就是将来事件发生后，通过它可以知道事件和哪个channel的事件
    SelectionKey sscKey = ssc.register(selector, 0, null);
    // key 只关注 accept 事件
    sscKey.interestOps(SelectionKey.OP_ACCEPT);
    log.debug("sscKey:{}", sscKey);
    ssc.bind(new InetSocketAddress(8080));
    while (true) {
        // 3. select 方法, 没有事件发生，线程阻塞，有事件，线程才会恢复运行
        // select 在事件未处理时，它不会阻塞, 事件发生后要么处理，要么取消，不能置之不理
        selector.select();
        // 4. 处理事件, selectedKeys 内部包含了所有发生的事件
        Iterator<SelectionKey> iter = selector.selectedKeys().iterator(); // accept, read
        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            // 处理key 时，要从 selectedKeys 集合中删除，否则下次处理就会有问题
            iter.remove();
            log.debug("key: {}", key);
            // 5. 区分事件类型
            if (key.isAcceptable()) { // 如果是 accept
                ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                SocketChannel sc = channel.accept();
                sc.configureBlocking(false);
                ByteBuffer buffer = ByteBuffer.allocate(16); // attachment
                // 将一个 byteBuffer 作为附件关联到 selectionKey 上
                SelectionKey scKey = sc.register(selector, 0, buffer);
                scKey.interestOps(SelectionKey.OP_READ);
                log.debug("{}", sc);
                log.debug("scKey:{}", scKey);
            } else if (key.isReadable()) { // 如果是 read
                try {
                    SocketChannel channel = (SocketChannel) key.channel(); // 拿到触发事件的channel
                    // 获取 selectionKey 上关联的附件
                    ByteBuffer buffer = (ByteBuffer) key.attachment();
                    int read = channel.read(buffer); // 如果是正常断开，read 的方法的返回值是 -1
                    if(read == -1) {
                        key.cancel();
                    } else {
                        split(buffer);
                        // 需要扩容
                        if (buffer.position() == buffer.limit()) {
                            ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
                            buffer.flip();
                            newBuffer.put(buffer);
                            key.attach(newBuffer);
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    key.cancel();  // 因为客户端断开了,因此需要将 key 取消（从 selector 的 keys 集合中真正删除 key）
                }
            }
        }
    }
}

private static void split(ByteBuffer source) {
    source.flip();
    for (int i = 0; i < source.limit(); i++) {
        // 找到一条完整消息
        if (source.get(i) == '\n') {
            int length = i + 1 - source.position();
            // 把这条完整消息存入新的 ByteBuffer
            ByteBuffer target = ByteBuffer.allocate(length);
            // 从 source 读，向 target 写
            for (int j = 0; j < length; j++) {
                target.put(source.get());
            }
            debugAll(target);
        }
    }
    source.compact();
}
```

注意：

- select 在事件发生后，就会将相关的 key 放入 selectedKeys 集合，但不会在处理完后从 selectedKeys 集合中移除，需要我们自己编码删除。
- cancel 会取消注册在 selector 上的 channel，并从 keys 集合中删除 key 后续不会再监听事件。

### 网络编程

结合了 Selector 的 NIO 主要应用网络编程。

#### 问题

Selector 选择器对象是线程安全的，但它们包含的键集合不是。通过 keys() 和 selectKeys() 返回的键的集合是 Selector 对象内部的私有的 Set 对象集合的直接引用,这些集合可能在任意时间被改变。

多个线程并发地访问一个选择器的键的集合可能出现线程安全问题，一种简单的思路：可以采用同步的方式进行访问，在执行选择操作时，选择器在 Selector 对象上进行同步，然后是已注册的键的集合，最后是已选择的键的集合。

但是在并发量大的时候，使用同一个线程处理连接请求以及消息服务，可能会出现拒绝连接的情况，这是因为当该线程在处理消息服务的时候，可能会无法及时处理连接请求，从而导致超时。

一个更好的策略：对所有的可选择通道使用一个选择器，并将对就绪通道的服务委托给其它线程。只需一个线程监控通道的就绪状态并使用一个协调好的的工作线程池来处理接收及发送数据。

#### 优化

针对上面的问题，我们做出一些优化：`分两组选择器`。

- 单线程配一个选择器，专门处理 accept 事件。
- 根据 CPU 核心数创建多个线程，每个线程配一个选择器，轮流处理 read 事件。

> Runtime.getRuntime().availableProcessors() 可以拿到 cpu 个数，但是如果工作在 docker 容器下，因为容器不是物理隔离的，会拿到物理 cpu 个数，而不是容器申请时的个数。
<br>
这个问题直到 JDK 10 才修复，使用 jvm 参数 UseContainerSupport 配置，默认开启。

##### 实现思路

创建一个负责处理 Accept 事件的 Boss 线程和多个负责处理 Read 事件的 Worker 线程。

Boss 线程执行的操作：
 
- 接受并处理 Accepet 事件，当 Accept 事件发生后，调用 Worker 的 `register(SocketChannel socket)` 方法，让 Worker 去处理 Read 事件，
  其中需要根据标识 robin 去判断将任务分配给哪个 Worker。

```java
// 创建固定数量的Worker
Worker[] workers = new Worker[4];
// 用于负载均衡的原子整数
AtomicInteger robin = new AtomicInteger(0);
// 负载均衡，轮询分配Worker
workers[robin.getAndIncrement()% workers.length].register(socket);
```

- `register(SocketChannel socket)` 方法会通过同步队列完成 Boss 线程与 Worker 线程之间的通信，让 SocketChannel 的注册任务被 Worker 线程执行。
添加任务后需要调用 selector.wakeup() 来唤醒被阻塞的 Selector。

```java
public void register(final SocketChannel socket) throws IOException {
    // 只启动一次
    if (!started) {
       // 初始化操作
    }
    // 向同步队列中添加SocketChannel的注册事件
    // 在Worker线程中执行注册事件
    queue.add(new Runnable() {
        @Override
        public void run() {
            try {
                socket.register(selector, SelectionKey.OP_READ);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    });
    // 唤醒被阻塞的Selector
    // select类似LockSupport中的park，wakeup的原理类似LockSupport中的unpark
    selector.wakeup();
}
```

Worker线程执行的操作：从同步队列中获取注册任务，并处理 Read 事件。

##### 实现代码

```java
public class ThreadsServer {
    public static void main(String[] args) {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            // 当前线程为Boss线程
            Thread.currentThread().setName("Boss");
            server.bind(new InetSocketAddress(8080));
            // 负责轮询Accept事件的Selector
            Selector boss = Selector.open();
            server.configureBlocking(false);
            server.register(boss, SelectionKey.OP_ACCEPT);
            // 创建固定数量的Worker
            Worker[] workers = new Worker[4];
            // 用于负载均衡的原子整数
            AtomicInteger robin = new AtomicInteger(0);
            for(int i = 0; i < workers.length; i++) {
                workers[i] = new Worker("worker-"+i);
            }
            while (true) {
                boss.select();
                Set<SelectionKey> selectionKeys = boss.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    // BossSelector负责Accept事件
                    if (key.isAcceptable()) {
                        // 建立连接
                        SocketChannel socket = server.accept();
                        System.out.println("connected...");
                        socket.configureBlocking(false);
                        // socket注册到Worker的Selector中
                        System.out.println("before read...");
                        // 负载均衡，轮询分配Worker
                        workers[robin.getAndIncrement()% workers.length].register(socket);
                        System.out.println("after read...");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class Worker implements Runnable {
        private Thread thread;
        private volatile Selector selector;
        private String name;
        private volatile boolean started = false;
        /**
         * 同步队列，用于Boss线程与Worker线程之间的通信
         */
        private ConcurrentLinkedQueue<Runnable> queue;

        public Worker(String name) {
            this.name = name;
        }

        public void register(final SocketChannel socket) throws IOException {
            // 只启动一次
            if (!started) {
                thread = new Thread(this, name);
                selector = Selector.open();
                queue = new ConcurrentLinkedQueue<>();
                thread.start();
                started = true;
            }
            
            // 向同步队列中添加SocketChannel的注册事件
            // 在Worker线程中执行注册事件
            queue.add(new Runnable() {
                @Override
                public void run() {
                    try {
                        socket.register(selector, SelectionKey.OP_READ);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            // 唤醒被阻塞的Selector
            // select类似LockSupport中的park，wakeup的原理类似LockSupport中的unpark
            selector.wakeup();
        }

        @Override
        public void run() {
            while (true) {
                try {
                    selector.select();
                    // 通过同步队列获得任务并运行
                    Runnable task = queue.poll();
                    if (task != null) {
                        // 获得任务，执行注册操作
                        task.run();
                    }
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while(iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        // Worker只负责Read事件
                        if (key.isReadable()) {
                            // 简化处理，省略细节
                            SocketChannel socket = (SocketChannel) key.channel();
                            ByteBuffer buffer = ByteBuffer.allocate(16);
                            socket.read(buffer);
                            buffer.flip();
                            ByteBufferUtil.debugAll(buffer);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
```