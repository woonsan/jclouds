/**
 * Licensed to jclouds, Inc. (jclouds) under one or more
 * contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  jclouds licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jclouds.route53.handlers;

import static com.google.common.base.Throwables.propagate;
import static org.jclouds.rest.internal.BaseRestApiExpectTest.payloadFromStringWithContentType;
import static org.jclouds.util.Strings2.toStringAndClose;
import static org.testng.Assert.assertEquals;

import java.io.IOException;

import org.jclouds.aws.AWSResponseException;
import org.jclouds.http.HttpCommand;
import org.jclouds.http.HttpRequest;
import org.jclouds.http.HttpResponse;
import org.jclouds.http.functions.config.SaxParserModule;
import org.jclouds.io.Payload;
import org.jclouds.rest.ResourceNotFoundException;
import org.jclouds.route53.InvalidChangeBatchException;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;

/**
 * 
 * @author Adrian Cole
 */
@Test(groups = "unit", singleThreaded = true, testName = "Route53ErrorHandlerTest" )
public class Route53ErrorHandlerTest {
   Route53ErrorHandler function = Guice.createInjector(new SaxParserModule()).getInstance(Route53ErrorHandler.class);

   HttpRequest request = HttpRequest.builder().method("POST")
         .endpoint("https://route53.amazonaws.com/2012-02-29/hostedzone/Z1PA6795UKMFR9/rrset")
         .addHeader("Host", "route53.amazonaws.com")
         .addHeader("Date", "Mon, 21 Jan 02013 19:29:03 -0800")
         .addHeader("X-Amzn-Authorization", "AWS3-HTTPS AWSAccessKeyId=identity,Algorithm=HmacSHA256,Signature=pylxNiLcrsjNRZOsxyT161JCwytVPHyc2rFfmNCuZKI=")
         .payload(payloadFromResource("/batch_rrs_request.xml")).build();
   HttpCommand command = command(request);

   @Test
   public void testInvalidChangeBatchException() throws IOException {
      HttpResponse response = HttpResponse.builder().statusCode(400)
                                                    .payload(payloadFromResource("/invalid_change_batch.xml")).build();
      function.handleError(command, response);

      InvalidChangeBatchException exception = InvalidChangeBatchException.class.cast(command.getException());

      assertEquals(exception.getMessages(), ImmutableSet.of(
            "Tried to create resource record set duplicate.example.com. type A, but it already exists",
            "Tried to delete resource record set noexist.example.com. type A, but it was not found"));
   }

   @Test
   public void testDeleteNotFound() throws IOException {

      HttpResponse response = HttpResponse.builder().statusCode(400)
            .payload(
                  payloadFromStringWithContentType(
                        "<ErrorResponse><Error><Type>Sender</Type><Code>InvalidChangeBatch</Code>"
                              + "<Message>Tried to delete resource record set krank.foo.bar., type TXT but it was not found</Message>"
                              + "</Error></ErrorResponse>", "application/xml")).build();

      function.handleError(command, response);

      assertEquals(command.getException().getClass(), ResourceNotFoundException.class);
      assertEquals(command.getException().getMessage(), "Tried to delete resource record set krank.foo.bar., type TXT but it was not found");

      AWSResponseException exception = AWSResponseException.class.cast(command.getException().getCause());

      assertEquals(exception.getError().getCode(), "InvalidChangeBatch");
   }

   private static HttpCommand command(final HttpRequest request) {
      return new HttpCommand() {

         private Exception exception;

         @Override
         public int getRedirectCount() {
            return 0;
         }

         @Override
         public int incrementRedirectCount() {
            return 0;
         }

         @Override
         public boolean isReplayable() {
            return false;
         }

         @Override
         public Exception getException() {
            return exception;
         }

         @Override
         public int getFailureCount() {
            return 0;
         }

         @Override
         public int incrementFailureCount() {
            return 0;
         }

         @Override
         public void setException(Exception exception) {
            this.exception = exception;
         }

         @Override
         public HttpRequest getCurrentRequest() {
            return request;
         }

         @Override
         public void setCurrentRequest(HttpRequest request) {

         }

      };
   }

   private Payload payloadFromResource(String resource) {
      try {
         return payloadFromStringWithContentType(toStringAndClose(getClass().getResourceAsStream(resource)),
               "application/xml");
      } catch (IOException e) {
         throw propagate(e);
      }
   }
}
