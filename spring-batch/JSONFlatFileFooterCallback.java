package com.company.project.module.process.writer;

import java.io.IOException;
import java.io.Writer;

import org.springframework.batch.item.file.FlatFileFooterCallback;

/**
 * Footer para cerrar el array JSON
 * @author rospena
 */
public class JSONFlatFileFooterCallback implements FlatFileFooterCallback {

	@Override
	public void writeFooter(Writer writer) throws IOException {
		writer.write("]"); 
	}
}
