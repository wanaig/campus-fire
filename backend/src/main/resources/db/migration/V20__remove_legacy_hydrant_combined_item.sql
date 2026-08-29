DELETE item
FROM inspection_item item
JOIN facility_type type ON type.id = item.facility_type_id
WHERE type.type_code = 'FIRE_HYDRANT'
  AND item.item_code = 'HOSE_VALVE'
  AND item.enabled = 0;
