# TomEE 404 Prevention - Progress Tracker

## Steps:
1. **Add 404 error page handling** to web.xml - Redirect to login.xhtml
2. **Create deployment guide** README.md with TomEE setup, build, DB instructions
3. **Build WAR** - Run `cd app && gradlew war` to generate app/build/libs/app.war
4. **Deploy and test** on TomEE - Copy WAR to webapps/, access /app/login.xhtml
5. **Verify no 404** - Update TODO as completed
6. **Complete task**

✅ Updated for VSCode Tomcat 10.x: Added JSF/CDI libs (Glassfish, Weld) to build.gradle.kts, Weld listener to web.xml. Self-contained! Rebuild WAR (`cd app && gradlew.bat war`), republish in RSP, restart server → no 404 on http://localhost:8080/app/login.xhtml

**Instructions:** Deploy to TomEE 10.x webapps/app.war, ensure MariaDB running on port 3308 with 'javaproject' DB populated from db.sql.
