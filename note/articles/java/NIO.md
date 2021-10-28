# NIO

Java NIO（Non-blocking I/O）是从 JDK 1.4 版本开始引入的一个新的 IO API，可以替代标准的 Java IO API。NIO 使用同步非阻塞的方式重写了老的 I/O 了，
即使我们不显式地使用 NIO 方式来编写代码，也能带来性能和速度的提高。这种提升不仅仅体现在文件读写（File I/O），同时也体现在网络读写（Network I/O）中。

NIO 速度的提升来自于使用了更接近操作系统 I/O 执行方式的结构：Channel（通道） 和 Buffer（缓冲区）。我们可以想象一个煤矿：通道就是连接矿层（数据）的矿井，缓冲区是运送煤矿的小车。
通过小车装煤，再从车里取矿。换句话说，我们不能直接和 Channel 交互; 我们需要与 Buffer 交互并将 Buffer 中的数据发送到 Channel 中；Channel 需要从 Buffer 中提取或放入数据。

### IO 和 NIO

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

#### Selector

Selector 是多路复用器选择器，它允许单线程处理多个 Channel。

使用 Selector，首先得向 Selector 注册  Channel，然后调用它的 select()。该方法会一直阻塞，直到某个注册的 Channel 有事件就绪。一旦这个方法返回，线程就可以处理这些事件，
事件的例子如建立新连接，数据接收等。




