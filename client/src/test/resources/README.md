Please note that SSL dependant tests will fail when test certificate is expired. 

In such case renew it to fix the problem:

```
$ keytool -genkey -alias testcert -validity 1465 -keyalg RSA -keystore keystore
$ keytool -export -keystore keystore -alias testcert -file testcert.cer
$ keytool -import -file testcert.cer -alias testcert -keystore truststore
```

