/*
 * Copyright (C) 2020
 * Andrew Smith <github.com/ChucklesTheBeard>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * Significant portions of this file are from Bert Wijnants - http://www.newpage.be/
 * Those portions are licensed under CC-BY-SA.
 */

package com.sheepit.client;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

public class ThrottlingMultipartBody extends RequestBody {
	protected final MultipartBody multipartBody;
	protected final OnProgressListener listener;
	
	protected ProgressForwardingSink progressForwardingSink;
	protected Configuration user_config;
	
	public ThrottlingMultipartBody(MultipartBody multipartBody, Configuration user_config, OnProgressListener listener) {
		this.multipartBody = multipartBody;
		this.listener = listener;
		this.user_config = user_config;
	}
	
	public MediaType type() {
		return multipartBody.type();
	}
	
	public String boundary() {
		return multipartBody.boundary();
	}
	
	public int size() {
		return multipartBody.size();
	}
	
	public List<MultipartBody.Part> parts() {
		return multipartBody.parts();
	}
	
	public MultipartBody.Part part(int index) {
		return multipartBody.part(index);
	}
	
	@Override public long contentLength() {
		try {
			return multipartBody.contentLength();
		}
		catch (IOException e) {
			e.printStackTrace(); // use log instead?
		}
		return -1;
	}
	
	@Override public MediaType contentType() {
		return multipartBody.contentType();
	}
	
	@Override public void writeTo(@NotNull BufferedSink sink) throws IOException {
		progressForwardingSink = new ProgressForwardingSink(sink, user_config);
		BufferedSink bufferedSink = Okio.buffer(progressForwardingSink);
		
		multipartBody.writeTo(bufferedSink);
		
		bufferedSink.flush();
	}
	
	protected final class ProgressForwardingSink extends ForwardingSink {
		public final static int CHUNK_DELAY = 100; // milliseconds
		private int CHUNK_SIZE = 0;
		private long bytesWritten = 0;
		private long lastIndex = 0;
		
		public ProgressForwardingSink(Sink delegate, Configuration user_config) {
			super(delegate);
			CHUNK_SIZE = user_config.getMaxUploadSpeed() / 8 / (1000 / CHUNK_DELAY) * 1024;
		}
		
		@Override public void write(@NotNull Buffer source, long byteCount) throws IOException {
			super.write(source, byteCount);
			if (CHUNK_SIZE > 0) {
				bytesWritten += byteCount;
				long index = bytesWritten / CHUNK_SIZE;
				
				if (index > lastIndex) {
					try {
						Thread.sleep(CHUNK_DELAY * (index - lastIndex));
					}
					catch (InterruptedException e) {
						//log.debug("chunk sleep interrupted")
					}
					lastIndex = index;
				}
				listener.onRequestProgress(bytesWritten, contentLength());
			}
		}
	}
	
	public interface OnProgressListener {
		void onRequestProgress(long bytesWritten, long contentLength);
	}
}
