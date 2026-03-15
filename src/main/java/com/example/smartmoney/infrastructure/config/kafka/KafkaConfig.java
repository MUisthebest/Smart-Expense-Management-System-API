package com.example.smartmoney.infrastructure.config.kafka;

@EnableKafka
@Configuration

public class KafkaConfig {
    @Bean
    public ProducerFactory <String, Object> producerFactory() {
        Map<String, Object> producer = new HashMap<>();

        producer.put(
                ProducerConfig.BOOTSTRAP_SERVER_CONFIG,
                "localhost:9092"
        );

        producer.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );
        return new DefaultKafkaProducerFactory<>(producer);
    }

    @Bean
    public KafkaTemplate<String,Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}




