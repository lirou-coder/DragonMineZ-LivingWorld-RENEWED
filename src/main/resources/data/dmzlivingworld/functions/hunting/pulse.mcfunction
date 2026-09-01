execute if score @s lw_scout_state matches 0 if score @s lw_huntcd matches 0 if score @s lw_frieza_rep matches ..-50 unless entity @e[tag=lw_frieza_scout,distance=..180,limit=1] if predicate dmzlivingworld:scout_roll run function dmzlivingworld:hunting/start_scout
execute if score @s lw_scout_state matches 1 run scoreboard players remove @s lw_scout_timer 1
execute if score @s lw_scout_state matches 1 if score @s lw_scout_timer matches ..0 if entity @e[tag=lw_frieza_scout,distance=..200,limit=1] run function dmzlivingworld:hunting/reinforcements
execute if score @s lw_scout_state matches 1 if score @s lw_scout_timer matches ..0 unless entity @e[tag=lw_frieza_scout,distance=..200,limit=1] run function dmzlivingworld:hunting/scout_lost
