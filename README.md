# Vanity Generator

This project will deploy a new app on Clever Cloud based on a typeform form. Setup the form webhook to send the right info to the server. Right now you need to have, firstname as shorttext, lastname as shorttext, email as email, phone number as short text and a picture upload field.

The form data is posted to the server. The picture URL is extracted and uploaded to cloudinary, an app is deployed on Clever Cloud with the data filled in the form, a text is sent with the address of the app.

## Clever Cloud configuration

```
CC_CONSUMER_KEY==************
CC_CONSUMER_SECRET==************
CC_PRE_RUN_HOOK=git clone https://github.com/ldoguin/vanity gitrepo
CC_TOKEN==************
CC_TOKEN_SECRET==************
CLOUDINARY_API_KEY==************
CLOUDINARY_API_SECRET==************
CLOUDINARY_CLOUD_NAME=yourCloudinaryCloud
FROM_NUMBER=+=************
JAVA_VERSION=8
MAVEN_DEPLOY_GOAL=spring-boot:run
NEXMO_API_KEY==************
NEXMO_API_SECRET==************
ORGA_ID=************
PORT=8080
```