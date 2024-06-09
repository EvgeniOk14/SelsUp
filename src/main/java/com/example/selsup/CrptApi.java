package com.example.selsup;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.*;

public class CrptApi
{
    //region Fields
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final Semaphore semaphore; // используется для ограничения количества запросов
    private final ScheduledExecutorService scheduler; // обновляет доступные разрешения семафора
    private final OkHttpClient httpClient; // клиент для отправки HTTP-запросов
    private final Gson gson; // сериализации и десериализации JSON
    //endregion

    //region Constructor
    public CrptApi(TimeUnit timeUnit, int requestLimit)
    {
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();

        // Регулярное обновление семафора в указанный промежуток времени
        scheduler.scheduleAtFixedRate(() ->
        {
            semaphore.release(requestLimit - semaphore.availablePermits());
        }, 0, 1, timeUnit);
    }
    //endregion

    public void createDocument(Document document, String signature) throws InterruptedException, IOException
    {
        semaphore.acquire(); // блокировка, если лимит запросов превышен
        try
        {
            //String jsonDocument = gson.toJson(document);

            JsonObject requestBody = new JsonObject();
            requestBody.add("description", gson.toJsonTree(document));
            requestBody.addProperty("signature", signature);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(RequestBody.create(requestBody.toString(), MediaType.get("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful())
                {
                    throw new IOException("Unexpected code " + response);
                }

                System.out.println(response.body().string());
            }

        }
        finally
        {
            semaphore.release(); // освобождает разрешение после завершения запроса

        }
    }

    public void shutdown()
    {
        scheduler.shutdown();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    /**
     * Внутренний класс для документа
     * **/
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document
    {
        private String participantInn;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private Product[] products;
        private String reg_date;
        private String reg_number;
    }

    /**
     * Внутренний класс для продукта
     * **/
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Product
    {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    // пример использования
    public class Main
    {
        public static void main(String[] args) throws InterruptedException, IOException
        {
            CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);

            // Пример документа
            CrptApi.Document document = new CrptApi.Document();

            // здесь заполнение объекта document какими-либо данными
            document.setParticipantInn("1234567890");
            document.setDoc_id("doc_001");
            document.setDoc_status("NEW");
            document.setDoc_type("LP_INTRODUCE_GOODS");
            document.setImportRequest(true);
            document.setOwner_inn("9876543210");
            document.setParticipant_inn("1234567890");
            document.setProducer_inn("1122334455");
            document.setProduction_date("2020-01-23");
            document.setProduction_type("PRODUCTION");
            document.setReg_date("2020-01-23");
            document.setReg_number("reg_001");

            // Пример продукта
            CrptApi.Product product = new CrptApi.Product();
            product.setCertificate_document("cert_doc");
            product.setCertificate_document_date("2020-01-23");
            product.setCertificate_document_number("cert_num");
            product.setOwner_inn("9876543210");
            product.setProducer_inn("1122334455");
            product.setProduction_date("2020-01-23");
            product.setTnved_code("tnved_code");
            product.setUit_code("uit_code");
            product.setUitu_code("uitu_code");

            // Добавляем продукт в документ
            document.setProducts(new CrptApi.Product[]{product});

            String signature = "фио_подпись";

            api.createDocument(document, signature);

            // Завершение работы
            api.shutdown();
        }
    }
}

