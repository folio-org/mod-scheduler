insert into test_mod_scheduler.timer(id, module_id, module_name, type, timer_descriptor)
values
    ('123e4567-e89b-12d3-a456-426614174000', 'mod-foo-1.0.0', 'mod-foo', 'USER', '{
        "id":"123e4567-e89b-12d3-a456-426614174000",
        "modified": "false",
        "enabled": "true",
        "moduleId": "mod-foo-1.0.0",
        "moduleName": "mod-foo",
        "type": "user",
        "routingEntry": {
           "methods": [
             "POST"
           ],
           "pathPattern": "/testb/timer/20",
           "unit": "second",
           "delay": "20"
        }
       }'
    ),
    ('123e4567-e89b-12d3-a456-426614174001', 'mod-foo-1.0.0', 'mod-foo', 'USER', '{
        "id": "123e4567-e89b-12d3-a456-426614174001",
        "enabled": "false",
        "modified": "false",
        "moduleId": "mod-foo-1.0.0",
        "moduleName": "mod-foo",
        "type": "user",
        "routingEntry": {
           "methods": [
             "POST"
           ],
           "pathPattern": "/testb/timer/1",
           "schedule": {
            "cron": "*/1 * * * *"
           }
        }
       }'
    ),
    ('123e4567-e89b-12d3-a456-426614174002', 'mod-foo-1.0.0', 'mod-foo', 'USER', '{
        "id": "123e4567-e89b-12d3-a456-426614174002",
        "modified": "false",
        "moduleId": "mod-foo-1.0.0",
        "moduleName": "mod-foo",
        "type": "user",
        "routingEntry": {
          "methods": [
            "POST"
          ],
          "pathPattern": "/testb/timer/3",
          "schedule": {
            "cron": "*/3 * * * *"
          }
        }
       }'
    );
