/*
 * FileWriter.java
 * Copyright (c) 2017
 * Authors: Ionut Damian, Michael Dietz, Frank Gaibler, Daniel Langerenken, Simon Flutura,
 * Vitalijs Krumins, Antonio Grieco
 * *****************************************************
 * This file is part of the Social Signal Interpretation for Java (SSJ) framework
 * developed at the Lab for Human Centered Multimedia of the University of Augsburg.
 *
 * SSJ has been inspired by the SSI (http://openssi.net) framework. SSJ is not a
 * one-to-one port of SSI to Java, it is an approximation. Nor does SSJ pretend
 * to offer SSI's comprehensive functionality and performance (this is java after all).
 * Nevertheless, SSJ borrows a lot of programming patterns from SSI.
 *
 * This library is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this library; if not, see <http://www.gnu.org/licenses/>.
 */

package hcm.ssj.file;

import android.text.TextUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import hcm.ssj.core.Cons;
import hcm.ssj.core.Consumer;
import hcm.ssj.core.Log;
import hcm.ssj.core.SSJFatalException;
import hcm.ssj.core.Util;
import hcm.ssj.core.event.Event;
import hcm.ssj.core.option.Option;
import hcm.ssj.core.stream.Stream;

import static hcm.ssj.file.FileCons.FILE_EXTENSION_STREAM;

/**
 * File writer for SSJ.<br>
 * Created by Frank Gaibler on 20.08.2015.
 */
public class FileWriter extends Consumer implements IFileWriter
{
    /**
     *
     */
    public class Options extends IFileWriter.Options
    {
        public final Option<String> separator = new Option<>("separator", FileCons.DELIMITER_ATTRIBUTE, String.class, "");
        public final Option<Cons.FileType> type = new Option<>("type", Cons.FileType.ASCII, Cons.FileType.class, "file type (ASCII or BINARY)");

        /**
         *
         */
        private Options()
        {
            super();
            addOptions();
        }
    }

    public final Options options = new Options();
    private Cons.FileType fileType;
    private FileOutputStream fileOutputStream = null;
    private FileOutputStream fileOutputStreamHeader = null;
    private BufferedOutputStream byteStream;
    private byte[] buffer;

    private int sampleCount = 0;
    private SimpleHeader simpleHeader;
    private StringBuilder stringBuilder;
    private File file;

    public FileWriter()
    {
        _name = this.getClass().getSimpleName();
    }

    /**
	 * @param stream_in Stream[]
	 */
    @Override
    public final void enter(Stream[] stream_in) throws SSJFatalException
    {
        if (stream_in.length > 1 || stream_in.length < 1)
        {
            throw new SSJFatalException("stream count not supported");
        }
        if (stream_in[0].type == Cons.Type.EMPTY || stream_in[0].type == Cons.Type.UNDEF)
        {
            throw new SSJFatalException("stream type not supported");
        }
        //create file
        if (options.filePath.get() == null)
        {
            Log.w("file path not set, setting to default " + FileCons.SSJ_EXTERNAL_STORAGE);
            options.filePath.set(FileCons.SSJ_EXTERNAL_STORAGE);
        }
        File fileDirectory = Util.createDirectory(options.filePath.parseWildcards());

        if (options.fileName.get() == null)
        {
            String defaultName = TextUtils.join("_", stream_in[0].desc) + "." + FILE_EXTENSION_STREAM;
            Log.w("file name not set, setting to " + defaultName);
            options.fileName.set(defaultName);
        }
        file = new File(fileDirectory, options.fileName.get());

        fileType = options.type.get();
        start(stream_in[0]);
    }

    /**
     * @param stream Stream
     */
    private void start(Stream stream)
    {
        File fileHeader = file;
        File fileReal;
        String path = fileHeader.getPath();
        if (path.endsWith(FileCons.TAG_DATA_FILE))
        {
            fileReal = fileHeader;
            fileHeader = new File(path.substring(0, path.length() - 1));
        } else if (fileHeader.getName().contains("."))
        {
            fileReal = new File(path + FileCons.TAG_DATA_FILE);
        } else
        {
            fileHeader = new File(path + "." + FILE_EXTENSION_STREAM);
            fileReal = new File(path + "." + FILE_EXTENSION_STREAM + FileCons.TAG_DATA_FILE);
        }
        fileOutputStreamHeader = getFileConnection(fileHeader, fileOutputStreamHeader);

        sampleCount = 0;
        fileOutputStream = getFileConnection(fileReal, fileOutputStream);

        if(fileType == Cons.FileType.BINARY) {
            byteStream = new BufferedOutputStream(fileOutputStream);
            buffer = new byte[stream.tot];
        }
        else if(fileType == Cons.FileType.ASCII) {
            stringBuilder = new StringBuilder();
        }
    }

    /**
     * @param stream_in Stream[]
	 * @param trigger
     */
    @Override
    protected final void consume(Stream[] stream_in, Event trigger) throws SSJFatalException
    {
        if (fileType == Cons.FileType.ASCII)
        {
            stringBuilder.delete(0, stringBuilder.length());
        }

        if(fileType == Cons.FileType.ASCII)
        {
            switch (stream_in[0].type)
            {
                case BOOL:
                {
                    boolean[] in = stream_in[0].ptrBool();
                    for (int i = 0, j = 0; i < stream_in[0].num; i++) {
                        for (int k = 0; k < stream_in[0].dim; k++, j++) {
                            stringBuilder.append(in[j]);
                            stringBuilder.append(options.separator.get());
                        }
                        stringBuilder.append(FileCons.DELIMITER_LINE);
                    }
                    sampleCount += stream_in[0].num;
                    write(stringBuilder.toString(), fileOutputStream);
                    break;
                }
                case BYTE:
                {
                    byte[] in = stream_in[0].ptrB();
                    for (int i = 0, j = 0; i < stream_in[0].num; i++)
                    {
                        for (int k = 0; k < stream_in[0].dim; k++, j++)
                        {
                            stringBuilder.append(in[j]);
                            stringBuilder.append(options.separator.get());
                        }
                        stringBuilder.append(FileCons.DELIMITER_LINE);
                    }
                    sampleCount += stream_in[0].num;
                    write(stringBuilder.toString(), fileOutputStream);
                    break;
                }
                case CHAR:
                {
                    char[] in = stream_in[0].ptrC();
                    for (int i = 0, j = 0; i < stream_in[0].num; i++)
                    {
                        for (int k = 0; k < stream_in[0].dim; k++, j++)
                        {
                            stringBuilder.append(in[j]);
                            stringBuilder.append(options.separator.get());
                        }
                        stringBuilder.append(FileCons.DELIMITER_LINE);
                    }
                    sampleCount += stream_in[0].num;
                    write(stringBuilder.toString(), fileOutputStream);
                    break;
                }
                case SHORT:
                {
                    short[] in = stream_in[0].ptrS();
                    for (int i = 0, j = 0; i < stream_in[0].num; i++)
                    {
                        for (int k = 0; k < stream_in[0].dim; k++, j++)
                        {
                            stringBuilder.append(in[j]);
                            stringBuilder.append(options.separator.get());
                        }
                        stringBuilder.append(FileCons.DELIMITER_LINE);
                    }
                    sampleCount += stream_in[0].num;
                    write(stringBuilder.toString(), fileOutputStream);
                    break;
                }
                case INT:
                {
                    int[] in = stream_in[0].ptrI();
                    for (int i = 0, j = 0; i < stream_in[0].num; i++)
                    {
                        for (int k = 0; k < stream_in[0].dim; k++, j++)
                        {
                            stringBuilder.append(in[j]);
                            stringBuilder.append(options.separator.get());
                        }
                        stringBuilder.append(FileCons.DELIMITER_LINE);
                    }
                    sampleCount += stream_in[0].num;
                    write(stringBuilder.toString(), fileOutputStream);
                    break;
                }
                case LONG:
                {
                    long[] in = stream_in[0].ptrL();
                    for (int i = 0, j = 0; i < stream_in[0].num; i++)
                    {
                        for (int k = 0; k < stream_in[0].dim; k++, j++)
                        {
                            stringBuilder.append(in[j]);
                            stringBuilder.append(options.separator.get());
                        }
                        stringBuilder.append(FileCons.DELIMITER_LINE);
                    }
                    sampleCount += stream_in[0].num;
                    write(stringBuilder.toString(), fileOutputStream);
                    break;
                }
                case FLOAT:
                {
                    float[] in = stream_in[0].ptrF();
                    for (int i = 0, j = 0; i < stream_in[0].num; i++)
                    {
                        for (int k = 0; k < stream_in[0].dim; k++, j++)
                        {
                            stringBuilder.append(in[j]);
                            stringBuilder.append(options.separator.get());
                        }
                        stringBuilder.append(FileCons.DELIMITER_LINE);
                    }
                    sampleCount += stream_in[0].num;
                    write(stringBuilder.toString(), fileOutputStream);
                    break;
                }
                case DOUBLE:
                {
                    double[] in = stream_in[0].ptrD();
                    for (int i = 0, j = 0; i < stream_in[0].num; i++)
                    {
                        for (int k = 0; k < stream_in[0].dim; k++, j++)
                        {
                            stringBuilder.append(in[j]);
                            stringBuilder.append(options.separator.get());
                        }
                        stringBuilder.append(FileCons.DELIMITER_LINE);
                    }
                    sampleCount += stream_in[0].num;
                    write(stringBuilder.toString(), fileOutputStream);
                    break;
                }
                default:
                    Log.w("unsupported data type");
                    break;
            }
        }
        else if(fileType == Cons.FileType.BINARY)
        {
            sampleCount += stream_in[0].num;
            Util.arraycopy(stream_in[0].ptr(), 0, buffer, 0, stream_in[0].tot);
            write(buffer, byteStream);
        }
    }

    /**
     * @param stream_in Stream[]
     */
    @Override
    public final void flush(Stream stream_in[]) throws SSJFatalException
    {
        end(stream_in[0]);
    }

    /**
     * @param stream Stream
     */
    private void end(Stream stream)
    {
        if(fileType == Cons.FileType.BINARY)
            byteStream = (BufferedOutputStream)closeStream(byteStream);

        fileOutputStream = (FileOutputStream)closeStream(fileOutputStream);

        writeHeader(stream);
        fileOutputStreamHeader = (FileOutputStream)closeStream(fileOutputStreamHeader);
    }

    private void writeHeader(Stream stream) {

        simpleHeader = new SimpleHeader();

        simpleHeader._ftype = fileType.name();
        simpleHeader._sr = String.valueOf(stream.sr);
        simpleHeader._dim = String.valueOf(stream.dim);
        simpleHeader._byte = String.valueOf(stream.bytes);
        simpleHeader._type = stream.type.name();
        simpleHeader._from = "0";
        simpleHeader._ms = String.valueOf(_frame.getStartTimeMs());

        SimpleDateFormat sdf = new SimpleDateFormat(SimpleHeader.DATE_FORMAT, Locale.getDefault());

        Date date = new Date(_frame.getStartTimeMs());
        simpleHeader._local = sdf.format(date);

        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        simpleHeader._system = sdf.format(date);

        writeLine(simpleHeader.getLine1(), fileOutputStreamHeader);
        writeLine(simpleHeader.getLine2(), fileOutputStreamHeader);
        writeLine(simpleHeader.getLine3(), fileOutputStreamHeader);
        writeLine(simpleHeader.getLine4(), fileOutputStreamHeader);

        simpleHeader._num = String.valueOf(sampleCount);
        simpleHeader._to = String.valueOf(stream.time + stream.num / stream.sr);
        writeLine(simpleHeader.getLine5(), fileOutputStreamHeader);
        writeLine(simpleHeader.getLine6(), fileOutputStreamHeader);
    }

    /**
     * @param stream FileOutputStream
     * @return FileOutputStream
     */
    private OutputStream closeStream(OutputStream stream)
    {
        if (stream != null)
        {
            try
            {
                stream.close();
                stream = null;
            } catch (IOException e)
            {
                Log.e("could not close writer", e);
            }
        }
        return stream;
    }

    /**
     * @param file   File
     * @param stream FileOutputStream
     * @return FileOutputStream
     */
    private FileOutputStream getFileConnection(File file, FileOutputStream stream)
    {
        try
        {
            stream = new FileOutputStream(file);
        } catch (FileNotFoundException e)
        {
            Log.e("file not found", e);
        }
        return stream;
    }

    /**
     * @param data   byte[]
     * @param stream BufferedOutputStream
     */
    private void write(byte[] data, BufferedOutputStream stream)
    {
        if (data != null)
        {
            try
            {
                stream.write(data);
            } catch (IOException e)
            {
                Log.e("could not write data", e);
            }
        }
    }

    /**
     * @param line   String
     * @param stream FileOutputStream
     */
    private void write(String line, FileOutputStream stream)
    {
        if (stream != null)
        {
            try
            {
                stream.write(line.getBytes());
            } catch (IOException e)
            {
                Log.e("could not write data", e);
            }
        }
    }

    /**
     * @param line   String
     * @param stream FileOutputStream
     */
    private void writeLine(String line, FileOutputStream stream)
    {
        if (stream != null)
        {
            try
            {
                line += FileCons.DELIMITER_LINE;
                stream.write(line.getBytes());
            } catch (IOException e)
            {
                Log.e("could not write line", e);
            }
        }
    }
}
