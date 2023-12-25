insert into test_mod_scheduler.timer(id, timer_descriptor)
values
    ('123e4567-e89b-12d3-a456-426614174000', '{
        "id":"123e4567-e89b-12d3-a456-426614174000",
        "modified": "false",
        "enabled": "true",
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
    ('123e4567-e89b-12d3-a456-426614174001', '{
        "id": "123e4567-e89b-12d3-a456-426614174001",
        "modified": "false",
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
    ('123e4567-e89b-12d3-a456-426614174002', '{
        "id": "123e4567-e89b-12d3-a456-426614174002",
        "modified": "false",
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
