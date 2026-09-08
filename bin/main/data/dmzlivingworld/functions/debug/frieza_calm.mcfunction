scoreboard players set @s lw_frieza_rep 0
scoreboard players set @s lw_huntcd 0
scoreboard players set @s lw_scout_state 0
scoreboard players set @s lw_scout_timer 0
kill @e[tag=lw_hunter,distance=..256]
tellraw @s [{"text":"[Living World] Frieza pursuit reset.","color":"gray"}]
