# java8

## stream流分组
```java
// 根据物料进行分组
Map<String, List<LinkedHashMap<String, Object>>> listMap = list.stream().collect(Collectors.groupingBy(item -> item.get("mcode").toString()));
```

## findFirst
```java
List<String> list = Arrays.asList("Vijay", "Suresh", "Vinod");
	String output = list.stream()
	  .filter(e -> e.startsWith("V")) // Vijay, Vinod
	  .findFirst() //Vijay
        .orElse("NA");
        System.out.println(output);
```