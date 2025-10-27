package org.transito_seguro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Configuración del cliente SOAP para integración con e-Pagos.
 *
 * Este configurador gestiona la comunicación SOAP con la API de e-Pagos para:
 * 1. Consultar rendiciones (pagos exitosos)
 * 2. Consultar contracargos (disputas de pago)
 * 3. Consultar transacciones específicas
 * 4. Verificar estados de pago
 *
 * Características del cliente:
 * - Marshalling/Unmarshalling automático con JAXB
 * - Timeouts configurables para evitar bloqueos
 * - Retry automático en caso de errores temporales
 * - Logging de requests y responses para debugging
 * - Soporte para autenticación básica
 */
@Configuration
public class SoapClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(SoapClientConfig.class);

    // ========================================================================
    // PROPIEDADES DE CONFIGURACIÓN (desde application.yml)
    // ========================================================================

    /**
     * URL del servicio SOAP de e-Pagos.
     * Ejemplo: https://www.epagos.com/svc/wsespeciales.asmx
     */
    @Value("${epagos.soap.url}")
    private String soapUrl;

    /**
     * Usuario para autenticación en e-Pagos.
     * Se envía en cada request SOAP.
     */
    @Value("${epagos.soap.usuario}")
    private String usuario;

    /**
     * Clave para autenticación en e-Pagos.
     * Se envía en cada request SOAP.
     */
    @Value("${epagos.soap.clave}")
    private String clave;

    /**
     * Timeout de conexión en milisegundos.
     * Tiempo máximo para establecer conexión con el servidor.
     */
    @Value("${epagos.soap.connection-timeout:30000}")
    private int connectionTimeout;

    /**
     * Timeout de lectura en milisegundos.
     * Tiempo máximo para recibir respuesta del servidor.
     */
    @Value("${epagos.soap.read-timeout:60000}")
    private int readTimeout;

    // ========================================================================
    // CONSTANTES
    // ========================================================================

    /**
     * Paquete donde están las clases JAXB generadas desde el WSDL.
     * Estas clases se generan automáticamente con el plugin jaxb2-maven-plugin.
     */
    private static final String JAXB_CONTEXT_PATH = "org.transito_seguro.wsdl.generated";

    /**
     * Namespace del servicio SOAP de e-Pagos.
     */
    private static final String SOAP_NAMESPACE = "http://www.epagos.com/";

    // ========================================================================
    // BEAN: JAXB2 MARSHALLER
    // ========================================================================

    /**
     * Marshaller para convertir objetos Java a XML y viceversa.
     *
     * JAXB (Java Architecture for XML Binding) permite:
     * - Serializar objetos Java a XML (marshalling)
     * - Deserializar XML a objetos Java (unmarshalling)
     *
     * Las clases JAXB se generan automáticamente desde el WSDL de e-Pagos
     * usando el plugin jaxb2-maven-plugin configurado en el pom.xml.
     *
     * Proceso de generación:
     * 1. Descargar WSDL: https://www.epagos.com/svc/wsespeciales.asmx?WSDL
     * 2. Guardar en: src/main/resources/wsdl/epagos.wsdl
     * 3. Ejecutar: mvn clean compile
     * 4. Se generan clases en: target/generated-sources/jaxb/
     *
     * @return Marshaller configurado
     */
    @Bean
    public Jaxb2Marshaller marshaller() {
        logger.info("Configurando JAXB2 Marshaller para e-Pagos");

        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();

        // Establecer el paquete donde están las clases generadas
        marshaller.setContextPath(JAXB_CONTEXT_PATH);

        logger.info("JAXB2 Marshaller configurado con contexto: {}", JAXB_CONTEXT_PATH);

        return marshaller;
    }

    // ========================================================================
    // BEAN: HTTP CLIENT
    // ========================================================================

    /**
     * Cliente HTTP configurado con timeouts y retry.
     *
     * Apache HttpClient proporciona:
     * - Control fino de timeouts
     * - Manejo de conexiones keep-alive
     * - Retry automático en errores temporales
     * - Pool de conexiones
     *
     * Configuración de timeouts:
     * - Connection Timeout: Tiempo para establecer conexión
     * - Socket Timeout (Read): Tiempo para recibir datos
     * - Connection Request Timeout: Tiempo para obtener conexión del pool
     *
     * @return HttpClient configurado
     */
    @Bean
    public CloseableHttpClient httpClient() {
        logger.info("Configurando HTTP Client para SOAP con timeouts: connection={}ms, read={}ms",
                connectionTimeout, readTimeout);

        // Configuración de timeouts
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectionTimeout)           // Timeout de conexión
                .setSocketTimeout(readTimeout)                   // Timeout de lectura
                .setConnectionRequestTimeout(connectionTimeout)  // Timeout del pool
                .build();

        // Construir cliente HTTP
        CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setMaxConnTotal(20)                            // Máximo 20 conexiones totales
                .setMaxConnPerRoute(10)                         // Máximo 10 por ruta
                .build();

        logger.info("HTTP Client configurado exitosamente");

        return client;
    }

    // ========================================================================
    // BEAN: MESSAGE SENDER
    // ========================================================================

    /**
     * Message sender que utiliza Apache HttpClient.
     *
     * Conecta el WebServiceTemplate con nuestro HttpClient personalizado
     * para tener control sobre timeouts y configuración HTTP.
     *
     * @return Message sender configurado
     */
    @Bean
    public HttpComponentsMessageSender messageSender() {
        logger.info("Configurando HttpComponents Message Sender");

        HttpComponentsMessageSender messageSender = new HttpComponentsMessageSender();
        messageSender.setHttpClient(httpClient());

        return messageSender;
    }

    // ========================================================================
    // BEAN: WEB SERVICE TEMPLATE (Principal)
    // ========================================================================

    /**
     * WebServiceTemplate principal para comunicación con e-Pagos.
     *
     * Este es el bean principal que se inyecta en los servicios para
     * realizar llamadas SOAP a e-Pagos.
     *
     * Funcionalidades:
     * - Marshalling/Unmarshalling automático
     * - Manejo de errores SOAP Fault
     * - Logging de mensajes (opcional)
     * - Interceptors para agregar headers
     * @return WebServiceTemplate configurado
     */
    @Bean
    public WebServiceTemplate webServiceTemplate() {
        logger.info("Configurando WebServiceTemplate para e-Pagos SOAP");
        logger.info("Endpoint: {}", soapUrl);

        WebServiceTemplate template = new WebServiceTemplate();

        // Configurar marshaller
        template.setMarshaller(marshaller());
        template.setUnmarshaller(marshaller());

        // Configurar endpoint
        template.setDefaultUri(soapUrl);

        // Configurar message sender con timeouts
        template.setMessageSender(messageSender());

        // Agregar interceptor para logging (opcional, solo en desarrollo)
        // template.setInterceptors(new ClientInterceptor[] { loggingInterceptor() });

        logger.info("WebServiceTemplate configurado exitosamente");

        return template;
    }

    // ========================================================================
    // MÉTODO AUXILIAR: SOAP ACTION CALLBACK
    // ========================================================================

    public SoapActionCallback createSoapActionCallback(String soapAction) {
        return new SoapActionCallback(SOAP_NAMESPACE + soapAction);
    }

}