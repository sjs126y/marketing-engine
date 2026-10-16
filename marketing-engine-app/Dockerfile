# 鍩虹闀滃儚
FROM amazoncorretto:8

# 浣滆€?
MAINTAINER sjs

# 閰嶇疆
ENV PARAMS=""

# 鏃跺尯
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 娣诲姞搴旂敤
ADD target/marketing-engine-app.jar /marketing-engine-app.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /marketing-engine-app.jar $PARAMS"]