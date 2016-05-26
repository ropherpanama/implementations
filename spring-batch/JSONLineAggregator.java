package com.company.project.module.process.writer;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.beans.factory.InitializingBean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Esta implementacion escribe ficheros con datos en formato json
 * la misma hace uso del commitInterval para escribir el contenido al archivo
 * 
 * @author rospena
 */
public class JSONLineAggregator<T> implements LineAggregator<T>, StepExecutionListener, InitializingBean {

	private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
	private String dateFormat = DEFAULT_DATE_FORMAT;
	private Gson gson;
	private boolean isFirstObject = true;

	@Override
	public ExitStatus afterStep(StepExecution stepExecution) {
		stepExecution.getExecutionContext().putString("isFirstObject", Boolean.toString(isFirstObject));
		return null;
	}

	@Override
	public void beforeStep(StepExecution stepExecution) {
		if (stepExecution.getExecutionContext().containsKey("isFirstObject")) {
			isFirstObject = Boolean.parseBoolean(stepExecution.getExecutionContext().getString("isFirstObject"));
		}
	}

	@Override
	public String aggregate(T item) {
		if (isFirstObject) {
			isFirstObject = false;
			return "[" + gson.toJson(item);
		}
		return "," + gson.toJson(item);
	}

	public Gson getGson() {
		return gson;
	}

	public void setGson(Gson gson) {
		this.gson = gson;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		gson = new GsonBuilder().setDateFormat(dateFormat).disableHtmlEscaping().create();
	}

	public String getDateFormat() {
		return dateFormat;
	}

	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}
}
