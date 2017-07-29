# JSFlow for Spring Web MVC

This is a project that integrates [Mozilla Rhino](https://github.com/mozilla/rhino), a JavaScript runtime for the Java Virtual Machine with [Spring Framework](https://projects.spring.io/spring-framework/), a popular enterprise framework on the Java platform in a specific way.

It delivers a controller component for Spring MVC that allows complex control flows spanning several webpages in web applications to be expressed as a single structured algorithm in JavaScript, putting the rich set of control flow structures and function reusability of a full-blown language at your fingertips. It transparently maps users' requests to the correct points of execution in your JavaScript code, even as users freely navigate your website using the browser's back button or even clone the execution of the flow using the "File->New Window" functionality! A flexible set of state persistence options (including one where all state management is shifted to the browser, for maximum server scalability, with full cryptographic support for prevention of tampering) is included.

The aim is to provide a system that amalgamates the rapid development benefits and flexibility of a dynamic language with the strength, scalability and versatility of the Java platform and the Spring framework.

spring-webmvc-jsflow can be used with Rhino version 1.7R2 or above, as well as with all 2.0 or higher Spring Framework versions.
