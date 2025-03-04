/*
 * Copyright © 2024, Kanton Bern
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package ch.bedag.dap.hellodata.commons.nats.bean;

import ch.bedag.dap.hellodata.commons.nats.annotation.JetStreamSubscribe;
import ch.bedag.dap.hellodata.commons.nats.exception.NatsException;
import io.nats.client.Connection;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

@Log4j2
public class NatsConfigBeanPostProcessor implements BeanPostProcessor {

    private final Connection natsConnection;
    private final List<SubscribeAnnotationThread> THREADS = new ArrayList<>();
    @Value("${spring.application.name}")
    private String appName;
    @Value("${hello-data.instance.name:}")
    private String instanceName;

    public NatsConfigBeanPostProcessor(Connection natsConnection) {
        this.natsConnection = natsConnection;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        final Class<?> clazz = bean.getClass();
        Arrays.stream(clazz.getMethods()).forEach(method -> {
            Optional<JetStreamSubscribe> subOpt = Optional.ofNullable(AnnotationUtils.findAnnotation(method, JetStreamSubscribe.class));
            subOpt.ifPresent(subscribeAnnotation -> {
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1 || !parameterTypes[0].equals(subscribeAnnotation.event().getDataClass())) {
                    throw new NatsException(
                            String.format("Method '%s' on bean with name '%s' must have a single parameter of type %s when using the @%s annotation.", method.toGenericString(),
                                          beanName, subscribeAnnotation.event().getDataClass().getName(), JetStreamSubscribe.class.getName()));
                }
                Class<?> returnType = method.getReturnType();
                if (!returnType.equals(CompletableFuture.class)) {
                    throw new NatsException(
                            String.format("Method '%s' on bean with name '%s' must return type %s when using the @%s annotation.", method.toGenericString(), beanName,
                                          CompletableFuture.class.getName(), JetStreamSubscribe.class.getName()));
                }
                createSubscribeAnnotationManagerThread(bean, method, subscribeAnnotation);
            });
        });
        return bean;
    }

    /**
     * Each annotated method in service ( {@link JetStreamSubscribe} ) with specific stream_subject id will have one manager thread to fetch messages and invoke beans
     * Will add another bean/method to invoke for existing subscription thread
     *
     * @param bean
     * @param method
     * @param subscribeAnnotation
     */
    private void createSubscribeAnnotationManagerThread(Object bean, Method method, JetStreamSubscribe subscribeAnnotation) {
        String stream = subscribeAnnotation.event().getStreamName();
        String subject = subscribeAnnotation.event().getSubject();
        String subscriptionId = stream + "_" + subject;
        SubscribeAnnotationThread subscribeAnnotationThreadFound = null;
        if (!THREADS.isEmpty()) {
            subscribeAnnotationThreadFound = THREADS.stream().filter(thread -> thread.getSubscriptionIds().contains(subscriptionId)).findFirst().orElse(null);
        }
        if (subscribeAnnotationThreadFound != null) {
            subscribeAnnotationThreadFound.getBeanWrappers().add(new BeanMethodWrapper(method, bean, subscriptionId));
        } else {
            ArrayList<BeanMethodWrapper> beanWrappers = new ArrayList<>(List.of(new BeanMethodWrapper(method, bean, subscriptionId)));
            String durableName = this.appName + "-" + stream + "-" + subject + (StringUtils.hasText(instanceName) ? "-" + instanceName : "");
            String durableNameBase64 = new String(Base64.getEncoder().encode(durableName.getBytes(StandardCharsets.UTF_8)));
            log.info("durable name for consumer: {}, Base64: {}", durableName, durableNameBase64);
            SubscribeAnnotationThread thread = new SubscribeAnnotationThread(natsConnection, subscribeAnnotation, beanWrappers, durableNameBase64);
            THREADS.add(thread);
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.submit(thread);
        }
    }
}
