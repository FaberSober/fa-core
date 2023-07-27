# java8

## stream流分组
```java
// 获取明细内容
List<LinkedHashMap<String,Object>> list = transferMapper.getTransferDetail(billNumber);

// 根据物料进行分组
Map<String, List<LinkedHashMap<String, Object>>> listMap = list.stream().collect(Collectors.groupingBy(item -> item.get("mcode").toString()));
```