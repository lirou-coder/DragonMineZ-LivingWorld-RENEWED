scoreboard players set @s lw_scout_state 2
scoreboard players set @s lw_huntcd 24
tellraw @s [{"text":"[Frieza Force] ","color":"light_purple","bold":true},{"text":"The scout reported your position. A patrol is closing in.","color":"gray"}]
execute positioned ^-38 ^ ^52 positioned over world_surface run summon dragonminez:saga_friezasoldier02 ~ ~1 ~ {Tags:["lw_hunter","lw_frieza_patrol"]}
execute positioned ^38 ^ ^58 positioned over world_surface run summon dragonminez:saga_friezasoldier03 ~ ~1 ~ {Tags:["lw_hunter","lw_frieza_patrol"]}
kill @e[tag=lw_frieza_scout,distance=..220]
scoreboard players set @s lw_scout_state 0
