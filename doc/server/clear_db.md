# 清空数据库日志、逻辑删除等数据

```sql
delete from base_rbac_role_menu where deleted = true;
delete from base_rbac_user_role where deleted = true;
```