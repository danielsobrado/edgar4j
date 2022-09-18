// package org.jds.edgar4j.service.impl;

// import java.net.URI;
// import java.net.http.HttpClient;
// import java.net.http.HttpRequest;
// import java.net.http.HttpResponse;

// import org.jds.edgar4j.entity.Form4;
// import org.jds.edgar4j.service.Form4Service;
// import org.springframework.stereotype.Service;

// import lombok.extern.slf4j.Slf4j;

// /**
//  * @author J. Daniel Sobrado
//  * @version 1.0
//  * @since 2022-09-18
//  */
// @Slf4j
// @Service
// public class Form4ServiceImpl implements Form4Service {

//         // if isDirector == 1:
//         //     owner = 'Director'
//         // elif isOfficer == 1:
//         //     owner = 'Officer'
//         // elif isTenOwner == 1:
//         //     owner = '10% Owner'
//         // elif isOther == 1:
//         //     owner = 'Other'
//         // else:
//         //     owner = 'Unknown'

//         public Form4 downloadForm4(String cik, String accessionNumber, String primaryDocument) {
//                 log.info("Download form 4");
//                 final HttpClient client = HttpClient.newHttpClient();
//                 HttpRequest request = HttpRequest.newBuilder()
//                         .uri(URI.create(companyTickersUrl))
//                         .build();
//                 client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
//                         .thenApply(HttpResponse::body)
//                         .thenAccept(System.out::println)
//                         .join();
//                 return new Form4();
//         }
        
//         void parseForm4() {
//                 log.info("Parse form 4");


//         }
    
// }
