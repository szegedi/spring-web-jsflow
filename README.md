# JSFlow for Spring Web MVC

This is a project that integrates [Mozilla Rhino](https://github.com/mozilla/rhino), a JavaScript runtime for the Java Virtual Machine with [Spring Framework](https://projects.spring.io/spring-framework/), a popular enterprise framework on the Java platform in a specific way.

It delivers a controller component for Spring MVC that allows complex control flows spanning several webpages in web applications to be expressed as a single structured algorithm in JavaScript, putting the rich set of control flow structures and function reusability of a full-blown language at your fingertips. It transparently maps users' requests to the correct points of execution in your JavaScript code, even as users freely navigate your website using the browser's back button or even clone the execution of the flow using the browser's "Duplicate Tab" functionality! A flexible set of state persistence options (including one where all state management is shifted to the browser, for maximum server scalability, with full cryptographic support for prevention of tampering) is included.

The aim is to provide a system that amalgamates the rapid development benefits and flexibility of a dynamic language with the strength, scalability and versatility of the Java platform and the Spring framework.

spring-webmvc-jsflow can be used with Rhino version 1.7R2 or above, as well as with all 2.0 or higher Spring Framework versions.

## Getting Started

We helpfully set up a separate [example project](szegedi/spring-web-jsflow-example) with a runnable example webapp and tutorial.

## Why does it exist?

For pretty much the same purpose [Spring Web Flow](http://projects.spring.io/spring-webflow/) exists. The main difference is that with JSFlow, you can express web flows as ordinary programs written in JavaScript, while with Spring Web Flow you must express them as state machines encoded in XML.

## Ok, then why a different implementation?

While we agree with the goals Spring Web Flow strives to achieve, we are not comfortable with their flow description language of choice. Namely, they chose to describe page flows in XML. At their heart, page flows are algorithms, and we feel that describing algorithms in a first-class imperative programming language is a more natural fit for the task. It gives you the full expressive power of _if_, _for_, _switch_, etc. statements as well as the ability to define functions and call them from multiple places in the flow, reusing common functionality. JavaScript is close enough in syntax to Java and is easy to learn. To efficiently edit Spring Web Flow flows, you need to hand craft their XML files, a task that looks complex enough that there is a [cottage industry of graphical editors](https://duckduckgo.com/?q=spring+web+flow+editor) for maintaining flowgraph XML documents in response to the complexity. In contrast, you can edit a JavaScript web flow in any text editor you'd otherwise find fit for writing a snippet of JavaScript in.

So, it boils down to a matter of having more than one choice if you want to develop web applications with Spring. If you're comfortable with flows expressed in XML, and eventually being forced to use graphical IDE graph builders to describe control flow of a program, use Spring Web Flow. If you prefer writing the same control flow in a proper programming language, you might discover JSFlow is right for you.

By using continuations, the state of your script is recorded in an encapsulation of its call stack (together with all local variables) whenever it sends back a HTTP response. Not only does it allow you to store state between two requests simply in script local variables, it also opens up quite wild possibilities. Namely, the users of your web application can navigate at will in your web application: use the back button of their browser few times then continue from there, or even duplicate a backed-to page and diverge in two different directions. Your web application will seamlessly assume the script states appropriate for the current page on the next request, as those states aren't thrown away. And remember that you only had to write a single, structured, mostly linear script for all of this! The system manages the mapping of user's unpredictable browsing behaviors to your structured-programming script for you!

At the moment, continuations can be stored in-memory, bound to a HTTP session, or for more sophisticated applications, they can be persisted to a JDBC data source or even let to be managed by the client's browser! Smart stubbing of shared objects is employed that both minimizes the size of the serialized state and allows continuations to be resumed in a different JVM than the one that originally created them, allowing for clustering and failover scenarios.

