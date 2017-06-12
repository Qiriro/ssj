/*
 * ImageData.java
 * Copyright (c) 2017
 * Authors: Ionut Damian, Michael Dietz, Frank Gaibler, Daniel Langerenken, Simon Flutura
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

package hcm.ssj.ml;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;

import java.util.Date;

/**
 * Encapsulates data necessary for camera image decoding.
 * Each image data object consists of two bitmaps, one for original
 * RGB values and another for cropped image.
 *
 * @author Vitaly
 */

public class ImageData
{
	private Bitmap rgbBitmap;
	private Bitmap croppedBitmap;

	Canvas canvas;

	Matrix cropToFrameTransform;
	Matrix frameToCropTransform;

	private int[] rgb;

	int width = 640;
	int height = 480;

	public ImageData(int inputSize, boolean maintainAspectRatio)
	{
		// Create bitmap for rgb values
		rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

		// Create bitmap for cropped image
		croppedBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888);

		rgb = new int[width * height];

		// Transform image to be of a quadratic form as Inception model only
		// accepts images with the same width and height
		cropToFrameTransform = new Matrix();
		frameToCropTransform = ImageUtils.getTransformationMatrix(
				width, height, inputSize, inputSize, 90, maintainAspectRatio
		);
		frameToCropTransform.invert(cropToFrameTransform);
		canvas = new Canvas(croppedBitmap);
	}

	public Bitmap createRgbBitmap(byte[] data)
	{
		// Decode yuv to rgb
		ImageUtils.YUVNV21ToRgb(rgb, data, width, height);

		// Set bitmap pixels to those saved in rgb
		rgbBitmap.setPixels(rgb, 0, width, 0, 0, width, height);

		// Crop bitmap to a quadratic form
		canvas.drawBitmap(rgbBitmap, frameToCropTransform, null);

		// Save image to external storage
		//ImageUtils.saveBitmap(croppedBitmap, new Date().toString() + "preview.png");

		return croppedBitmap;
	}
}
