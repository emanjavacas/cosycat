<h1>Cosycat 
<span>
<em>
<sub><sup>(Collaborative Synchronized Corpus Annotation Tool)</sup></sub>
</em>
</span>
</h1>

For more documentation see [the wiki](https://github.com/emanjavacas/cosycat/wiki).

## Introduction

Cosycat is a web-based corpus query and annotation interface with a focus on
multiuser **synchronization** and **version control**. The application is designed 
with an emphasis on **modularity** and **reusability**.

![App Screenshot](https://github.com/emanjavacas/cosycat/raw/master/doc/img/client.png)

### What can I do with Cosycat?

The major goal of Cosycat is to enable synchronized multiuser annotation of corpora.
While Cosycat can absolutely be used for annotating typical NLP resources (such as
Part-of-Speech tagging a newspaper corpus), it is mostly geared towards (preferrably
team-based) Corpus Linguistic research aimed at statistically analyzing 
particular constructions that have to be first retrieved and annotated.

Cosycat allows multiple users to analyze the same corpus in an interactive way,
keeping their annotations synchronized with each other and receiving live feedback
from each other actions. Each change to the current body of corpus annotations 
(e.g. each time an annotation is introduced, modified or removed by a user) is
recorded in a version control fashion which allows to easily backtrace the annotation
history.

### How does it work?

Cosycat is built taking advantage of modern browser features such as WebSockets or LocalStorage,
recent developments in reactive UI programming (see the React framework), as well as the
flexibility of NoSQL databases.

1.  Architecture

    Cosycat follows the following client-server architecture:
    
	<p align="center"><img src="./doc/img/app-remote.jpg" width="650px"></p>
    
    The client interacts with the local annotation database and with the (possibly) remote server
    via HTTP. This gives you the advantage of not having to host corpus yourself (although, this
    is certainly possible).

2.  User interaction

    User activity is routed through the server and then propagated to the rest endpoints (users)
    that are currently using the application. Cosycat informs users of each others activities using
    a built-in notification system.

3.  Projects (access rights, pub/sub notifications)

    A central organizatory piece of Cosycat is the "project". Projects serve multiple purposes:
    
    -   Control notification verbosity (subscribing/unsubscribing from projects)
    -   Grouping annotations into different
    -   Advanced: (delayed merge) use projects as local working copies.

4.  Version Control

    Currently, Cosycat allows you to keep track of all changes effected over the annotation database.
    This is done with a [small module](https://github.com/emanjavacas/cosycat/blob/master/src/clj/cosycat/vcs.clj),
	that stores changes to a MongoDB document in a version-controlled
    collection using a traditional lock system for data consistency.
