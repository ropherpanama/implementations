##SpringBatch: [JSONItemWriter](https://github.com/ropherpanama/implementations/blob/master/spring-batch/JSONItemWriter.java)

Esta clase es una adaptación de la clase [FlatFileItemWriter<T>](https://docs.spring.io/spring-batch/apidocs/org/springframework/batch/item/file/FlatFileItemWriter.html) de [spring-batch](http://projects.spring.io/spring-batch/).
La misma fué modificada para solventar la necesidad de escribir objetos JSON a un archivo.

Un ItemWriter recibe una lista de objetos que deben ser escritos al archivo, la modificación consiste en que, justo antes de escribir los datos al archivo; son transformados a JSON mediante [Gson](https://github.com/google/gson).

Los datos (por la particular necesidad) son escritos en una sola línea y siempre como un array (aunque sea un solo elemento). Ejemplo

    [{"nombre":"Rosendo","apellido":"Ropher"},{"nombre":"Jack","apellido":"Daniels"},{"nombre":"Axel","apellido":"Rose"}]

Si la lista de items recibida por el writer no contine objeto alguno, el archivo se crea sin contenido.

#Uso

###XML Config

**Step config**

Se configura un `batch:flow`, asigna a el atributo `writer` la referencia al custom writer (`itemWriterThisTask`)

    <batch:flow>
				<batch:step id="ThisTask">
					<batch:tasklet>
						<batch:chunk reader="cursorReaderThisTask" writer="itemWriterThisTask"
							commit-interval="1000" />
					</batch:tasklet>
				</batch:step>
			</batch:flow>

Define el `writer` así

    <bean id="itemWriterThisTask" scope="step" class="com.company.project.module.process.writer.JSONItemWriter">
	    <property name="resource" value="#{jobParameters['fileThisTask']}" />
	    <property name="shouldDeleteIfExists" value="true" />
	</bean>

Para este ejemplo (por la necesidad específica) solo se definieron los atributos `resource` (porque es el archivo de salida) y `shouldDeleteIfExists` (porque así lo requiere la necesidad)

Otros atributos (`<property>`) que se mantienen, pero que no se ha probado su comportamiento son los siguientes:

    private boolean forceSync = false;
	private boolean shouldDeleteIfExists = true;
	private boolean shouldDeleteIfEmpty = false;
	private String encoding = OutputState.DEFAULT_CHARSET;
	private boolean transactional = DEFAULT_TRANSACTIONAL;
	private boolean append = false;
	
El `package` debe ser el paquete en donde ubiques la clase, en este caso `com.company.project.module.process.writer`

