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
ADD target/big-market-sjs-app.jar /big-market-sjs-app.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /big-market-sjs-app.jar $PARAMS"]