# 基础镜像
FROM amazoncorretto:8

# 作者
MAINTAINER sjs

# 配置
ENV PARAMS=""

# 时区
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 添加应用
ADD target/marketing-engine-app.jar /marketing-engine-app.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /marketing-engine-app.jar $PARAMS"]