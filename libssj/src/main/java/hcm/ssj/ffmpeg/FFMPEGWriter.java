/*
 * FFMPEGWriter.java
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

package hcm.ssj.ffmpeg;

import android.graphics.ImageFormat;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;

import java.nio.ByteBuffer;

import hcm.ssj.core.Cons;
import hcm.ssj.core.Consumer;
import hcm.ssj.core.Log;
import hcm.ssj.core.SSJFatalException;
import hcm.ssj.core.event.Event;
import hcm.ssj.core.option.Option;
import hcm.ssj.core.option.OptionList;
import hcm.ssj.core.stream.ImageStream;
import hcm.ssj.core.stream.Stream;

/**
 * Created by Michael Dietz on 04.09.2017.
 */

public class FFMPEGWriter extends Consumer
{
	/**
	 * FFMPEG writer options
	 */
	public class Options extends OptionList
	{
		public final Option<String> url = new Option<>("url", "/mnt/sdcard/output.mp4", String.class, "Url (file path or streaming address, e.g. udp://<ip:port>)");
		public final Option<Boolean> stream = new Option<>("stream", false, Boolean.class, "Set this flag for very fast decoding in streaming applications (forces h264 codec)");
		public final Option<String> format = new Option<>("format", "mp4", String.class, "Default output format, set to 'mpegts' in streaming applications");
		public final Option<Integer> bitRate = new Option<>("bitRate", 500, Integer.class, "Bitrate in kB/s");

		/**
		 *
		 */
		protected Options()
		{
			addOptions();
		}
	}

	public final Options options = new Options();

	private FFmpegFrameRecorder writer;
	private Frame imageFrame;
	private int pixelFormat;

	private int width;
	private int height;
	private int bufferSize;
	private byte[] frameBuffer;
	private int frameInterval;
	private long frameTime;
	private long startTime;

	public FFMPEGWriter()
	{
		_name = this.getClass().getSimpleName();
	}

	@Override
	public void enter(Stream[] stream_in) throws SSJFatalException
	{
		if (stream_in.length != 1)
		{
			throw new SSJFatalException("Stream count not supported");
		}
		if (stream_in[0].type != Cons.Type.IMAGE)
		{
			throw new SSJFatalException("Stream type not supported");
		}

		frameInterval = (int) (1.0 / stream_in[0].sr * 1000 + 0.5);

		width = ((ImageStream) stream_in[0]).width;
		height = ((ImageStream) stream_in[0]).height;

		writer = new FFmpegFrameRecorder(options.url.get(), width, height, 0);
		writer.setFormat(options.format.get());
		writer.setFrameRate(stream_in[0].sr);
		writer.setVideoBitrate(options.bitRate.get() * 1000);

		// Streaming options
		if (options.stream.get())
		{
			writer.setVideoCodec(avcodec.AV_CODEC_ID_H264);
			writer.setVideoOption("tune", "zerolatency");
			writer.setVideoOption("preset", "ultrafast");
			writer.setVideoOption("crf", "23");
			writer.setVideoOption("x264opts", "bframes=0:force-cfr:no-mbtree:sliced-threads:sync-lookahead=0:rc-lookahead=0:intra-refresh=1:keyint=1");
			writer.setInterleaved(true);

			// Keyframe interval
			writer.setGopSize((int) stream_in[0].sr);
		}

		bufferSize = width * height;

		// Initialize frame
		switch (((ImageStream) stream_in[0]).format)
		{
			case ImageFormat.NV21:
				imageFrame = new Frame(width, height, Frame.DEPTH_UBYTE, 2);
				pixelFormat = avutil.AV_PIX_FMT_NONE; // AV_PIX_FMT_NV21
				bufferSize *= 1.5;
				break;
			case ImageFormat.FLEX_RGB_888:
				imageFrame = new Frame(width, height, Frame.DEPTH_UBYTE, 3);
				pixelFormat = avutil.AV_PIX_FMT_RGB24;
				bufferSize *= 3;
				break;
			case ImageFormat.FLEX_RGBA_8888:
				imageFrame = new Frame(width, height, Frame.DEPTH_UBYTE, 4);
				pixelFormat = avutil.AV_PIX_FMT_RGBA;
				bufferSize *= 4;
				break;
		}

		frameBuffer = new byte[bufferSize];

		frameTime = 0;
		startTime = 0;

		try
		{
			writer.start();
		}
		catch (FrameRecorder.Exception e)
		{
			Log.e("Error while starting writer", e);
		}
	}

	@Override
	protected void consume(Stream[] stream_in, Event trigger) throws SSJFatalException
	{
		// Get bytes
		byte[] in = stream_in[0].ptrB();

		if (startTime == 0)
		{
			startTime = System.currentTimeMillis() - (stream_in[0].num - 1) * frameInterval;
		}

		frameTime = System.currentTimeMillis() - (stream_in[0].num - 1) * frameInterval;

		// Loop through frames
		for (int i = 0; i < stream_in[0].num; i++)
		{
			if (in.length > bufferSize)
			{
				System.arraycopy(in, i * bufferSize, frameBuffer, 0, bufferSize);
			}
			else
			{
				frameBuffer = in;
			}

			writeFrame(frameBuffer, frameTime + i * frameInterval);
		}
	}

	private void writeFrame(byte[] frameData, long time)
	{
		try
		{
			// Update frame
			((ByteBuffer) imageFrame.image[0].position(0)).put(frameData);

			// Update timestamp
			long t = 1000 * (time - startTime);
			if (t > writer.getTimestamp())
			{
				writer.setTimestamp(t);
			}

			// Write frame
			writer.record(imageFrame, pixelFormat);
		}
		catch (FrameRecorder.Exception e)
		{
			Log.e("Error while writing frame", e);
		}
	}

	@Override
	public void flush(Stream[] stream_in) throws SSJFatalException
	{
		try
		{
			writer.stop();
			writer.release();
		}
		catch (FrameRecorder.Exception e)
		{
			Log.e("Error while stopping writer", e);
		}
	}
}
