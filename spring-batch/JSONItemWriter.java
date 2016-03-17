package com.company.project.module.process.writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.WriteFailedException;
import org.springframework.batch.item.WriterNotOpenException;
import org.springframework.batch.item.file.ResourceAwareItemWriterItemStream;
import org.springframework.batch.item.support.AbstractItemStreamItemWriter;
import org.springframework.batch.item.util.FileUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import com.google.gson.Gson;

/**
 * <p> Dado que a la fecha (2016-03-17) spring-batch no implementa
 * un writer para tipos JSON específicamente, se ha optado por crear
 * esta implementacion de la clase FlatFileItemWriter para que sea capaz
 * de escribir archivos con data en formato JSON.
 * @author rospena 
 * 
 * <p>Esta clase ha sido una adaptación a una necesidad. Todos los créditos originales son de las siguientes personas: 
 * 
 * @author Waseem Malik
 * @author Tomas Slanina
 * @author Robert Kasanicky
 * @author Dave Syer
 * @author Michael Minella
 */
public class JSONItemWriter<T> extends AbstractItemStreamItemWriter<T>
		implements ResourceAwareItemWriterItemStream<T>, InitializingBean {

	private Gson gson = new Gson();
	private static final boolean DEFAULT_TRANSACTIONAL = true;
	protected static final Log logger = LogFactory.getLog(JSONItemWriter.class);
	private Resource resource;
	private OutputState state = null;
	private boolean forceSync = false;
	private boolean shouldDeleteIfExists = true;
	private boolean shouldDeleteIfEmpty = false;
	private String encoding = OutputState.DEFAULT_CHARSET;
	private boolean transactional = DEFAULT_TRANSACTIONAL;
	private boolean append = false;

	public JSONItemWriter() {
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (append) {
			shouldDeleteIfExists = false;
		}
	}

	public void setForceSync(boolean forceSync) {
		this.forceSync = forceSync;
	}

	@Override
	public void setResource(Resource resource) {
		this.resource = resource;
	}

	public void setEncoding(String newEncoding) {
		this.encoding = newEncoding;
	}

	public void setShouldDeleteIfExists(boolean shouldDeleteIfExists) {
		this.shouldDeleteIfExists = shouldDeleteIfExists;
	}

	public void setAppendAllowed(boolean append) {
		this.append = append;
		this.shouldDeleteIfExists = false;
	}

	public void setShouldDeleteIfEmpty(boolean shouldDeleteIfEmpty) {
		this.shouldDeleteIfEmpty = shouldDeleteIfEmpty;
	}

	public void setTransactional(boolean transactional) {
		this.transactional = transactional;
	}

	@Override
	public void write(List<? extends T> items) throws Exception {

		if (!getOutputState().isInitialized()) {
			throw new WriterNotOpenException("Writer must be open before it can be written to");
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Writing to flat file with " + items.size() + " items.");
		}

		OutputState state = getOutputState();
		StringBuilder lines = new StringBuilder();

		if (logger.isDebugEnabled()) {
			logger.debug("Writing to flat file : " + gson.toJson(items));
		}

		lines.append(gson.toJson(items));

		try {
			state.write(lines.toString());
		} catch (IOException e) {
			throw new WriteFailedException("Could not write data. The file may be corrupt.", e);
		}
	}

	@Override
	public void close() {
		super.close();
		if (state != null) {
			try {
				if (state.outputBufferedWriter != null) {
					state.outputBufferedWriter.flush();
				}
			} catch (IOException e) {
				throw new ItemStreamException("Failed to write footer before closing", e);
			} finally {
				state.close();
				if (state.linesWritten == 0 && shouldDeleteIfEmpty) {
					try {
						resource.getFile().delete();
					} catch (IOException e) {
						throw new ItemStreamException("Failed to delete empty file on close", e);
					}
				}
				state = null;
			}
		}
	}

	@Override
	public void open(ExecutionContext executionContext) throws ItemStreamException {
		super.open(executionContext);

		Assert.notNull(resource, "The resource must be set");

		if (!getOutputState().isInitialized()) {
			doOpen(executionContext);
		}
	}

	private void doOpen(ExecutionContext executionContext) throws ItemStreamException {
		OutputState outputState = getOutputState();

		try {
			outputState.initializeBufferedWriter();
		} catch (IOException ioe) {
			throw new ItemStreamException("Failed to initialize writer", ioe);
		}
	}

	@Override
	public void update(ExecutionContext executionContext) {
		super.update(executionContext);
		if (state == null) {
			throw new ItemStreamException("ItemStream not open or already closed.");
		}

		Assert.notNull(executionContext, "ExecutionContext must not be null");
	}

	private OutputState getOutputState() {
		if (state == null) {
			File file;
			try {
				file = resource.getFile();
			} catch (IOException e) {
				throw new ItemStreamException("Could not convert resource to file: [" + resource + "]", e);
			}
			Assert.state(!file.exists() || file.canWrite(), "Resource is not writable: [" + resource + "]");
			state = new OutputState();
			state.setDeleteIfExists(shouldDeleteIfExists);
			state.setAppendAllowed(append);
			state.setEncoding(encoding);
		}
		return state;
	}

	private class OutputState {
		private static final String DEFAULT_CHARSET = "UTF-8";
		private FileOutputStream os;
		Writer outputBufferedWriter;
		FileChannel fileChannel;
		String encoding = DEFAULT_CHARSET;
		boolean restarted = false;
		long lastMarkedByteOffsetPosition = 0;
		long linesWritten = 0;
		boolean shouldDeleteIfExists = true;
		boolean initialized = false;
		private boolean append = false;

		public void setAppendAllowed(boolean append) {
			this.append = append;
		}

		public void setDeleteIfExists(boolean shouldDeleteIfExists) {
			this.shouldDeleteIfExists = shouldDeleteIfExists;
		}

		public void setEncoding(String encoding) {
			this.encoding = encoding;
		}

		public void close() {
			initialized = false;
			restarted = false;
			try {
				if (outputBufferedWriter != null) {
					outputBufferedWriter.close();
				}
			} catch (IOException ioe) {
				throw new ItemStreamException("Unable to close the the ItemWriter", ioe);
			} finally {
				if (!transactional) {
					closeStream();
				}
			}
		}

		private void closeStream() {
			try {
				if (fileChannel != null) {
					fileChannel.close();
				}
			} catch (IOException ioe) {
				throw new ItemStreamException("Unable to close the the ItemWriter", ioe);
			} finally {
				try {
					if (os != null) {
						os.close();
					}
				} catch (IOException ioe) {
					throw new ItemStreamException("Unable to close the the ItemWriter", ioe);
				}
			}
		}

		public void write(String line) throws IOException {
			if (!initialized) {
				initializeBufferedWriter();
			}

			outputBufferedWriter.write(line);
			outputBufferedWriter.flush();
		}

		public void truncate() throws IOException {
			fileChannel.truncate(lastMarkedByteOffsetPosition);
			fileChannel.position(lastMarkedByteOffsetPosition);
		}

		private void initializeBufferedWriter() throws IOException {

			File file = resource.getFile();
			FileUtils.setUpOutputFile(file, restarted, append, shouldDeleteIfExists);

			os = new FileOutputStream(file.getAbsolutePath(), true);
			fileChannel = os.getChannel();

			outputBufferedWriter = getBufferedWriter(fileChannel, encoding);
			outputBufferedWriter.flush();

			Assert.state(outputBufferedWriter != null);

			if (restarted) {
				checkFileSize();
				truncate();
			}

			initialized = true;
		}

		public boolean isInitialized() {
			return initialized;
		}

		private Writer getBufferedWriter(FileChannel fileChannel, String encoding) {
			try {
				final FileChannel channel = fileChannel;

				Writer writer = new BufferedWriter(Channels.newWriter(fileChannel, encoding)) {
					@Override
					public void flush() throws IOException {
						super.flush();
						if (forceSync) {
							channel.force(false);
						}
					}
				};

				return writer;
			} catch (UnsupportedCharsetException ucse) {
				throw new ItemStreamException("Bad encoding configuration for output file " + fileChannel, ucse);
			}
		}

		private void checkFileSize() throws IOException {
			long size = -1;

			outputBufferedWriter.flush();
			size = fileChannel.size();

			if (size < lastMarkedByteOffsetPosition) {
				throw new ItemStreamException("Current file size is smaller than size at last commit");
			}
		}
	}
}
