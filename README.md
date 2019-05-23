#Alexey Shirmanov 

[LinkedIn](https://www.linkedin.com/in/alexey-shirmanov/) | [CV](http://ashirman.github.io) | ashirman8@gmail.com

- step 0: made a fork of the repo and configured environment. because of already installed gradle on my machine gradlew 5.5.1 
did not work properly from IDEA. Fixed it by setting 'Service directory path' IDEA's property to custom value.

- step 1: brain storming. carefully read given description few times and tried to investigate available industrial solutions 
 to be able to know existing approaches. Then tried to generate few ideas how it could be solved.

    - idea 1. use Kotlin's coroutines and/or java's threads (via thread pool) to be able to pay invoices as a single long-running job. 
    Eg reading available invoices with 'pending' status and execute requests via payment provider to handle these invoices.
    The advantage is that that's relatively simple solution and could be implemented really fast. There are some disadvantages of 
    this approach as well: for real-world amount of data the job execution will take long time (depending on payment provider performance and number of invoices).
    plus we need to read the entire collection of invoices from DB and lock the DB table. Need to note that this approach is not well scalable (how to split these invoices 
    between let say 10 nodes?) and require some tricky login for error handling. 
    - idea 2: use scheduler and schedule invoice payments individually. I selected Quartz as a job scheduler because it is a widely known 
    open-sourced solution with pretty active community. So in case of issues it's possible to read forums, stackowerflow, etc. It's is mentioned that 
    Quartz works well as for small tools as well for large enterprise e-commerce solutions thanks to clustering support. comparing to previous idea this 
    approach can be scaled pretty well as well as more reliable. Also we have some place for tuning and extensions for example 'spread' invoices so
    to be able not to perform too many request per second for 3rd part payment provider (it can be important)
    
- step 2: implementation and testing. there were few places where there is no exactly 1 right solution so selected approaches must be discussed.
    - add customer status enum and appropriate dal and service methods. need to mark customer as active/inactive in few cases: in case of exception from payment provider. it means there can be inconsistencies
    between us and payment provider DB and support team could investigate these cases individually.
    Also if amount of customer's money not enough because of whatever reasons also mark customer as INACTIVE. it's possible
    to notify customers about such issues via email/push/calls/ets but it is not a part of this task
    - implement billing service and appropriate quartz job. this is the core of the solution. so, every exception from payment provider is processing 
    differently. CustomerNotFoundException - mark customer as inactive as a start point (read about it above). CurrencyMismatchException - try to convert currency and resubmit another invoice.
    NetworkException - try to pay later by resubmitting job.
    
   For every scenario I tried to keep balance between effort and problem which I tried to solve (in other words makes no sense to spend a lot of time for scenarios which will never happen because this is still a prototype).
    
What solution does NOT contain (only because I had limited time for implementation. sorry!)

   - transactions between DB and Quartz. Quartz supports integration with transaction managers (mostly using Spring framework) but I had no time to integrate it.
   
   - single point of params configuration. so far in few places params such as number of retry attempts passed in constructor 
  and in few places values are hardcoded. so, it's pretty typical task and it's possible to create a kind of configuration service/environment then 
  configure it in env variables, files, etc to be able to tune it.
    - APIs for invoice payment history tracking. eg after successful/unsuccessful transaction we should record it to DB so it would be possible 
    to know the history of invoice payment (eg customer can request it, support may need it for investigation etc)
    
Totally I spent around 2 days to implement test and describe it. I will be happy to discuss selected approaches in details.

## Added library
* [Quartz](http://www.quartz-scheduler.org/) - job scheduling library

## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will pay those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

### Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── pleo-antaeus-app
|
|       Packages containing the main() application. 
|       This is where all the dependencies are instantiated.
|
├── pleo-antaeus-core
|
|       This is where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|
|       Module interfacing with the database. Contains the models, mappings and access layer.
|
├── pleo-antaeus-models
|
|       Definition of models used throughout the application.
|
├── pleo-antaeus-rest
|
|        Entry point for REST API. This is where the routes are defined.
└──
```

## Instructions
Fork this repo with your solution. We want to see your progression through commits (don’t commit the entire solution in 1 step) and don't forget to create a README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

Happy hacking 😁!

## How to run
```
./docker-start.sh
```

## Libraries currently in use
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
