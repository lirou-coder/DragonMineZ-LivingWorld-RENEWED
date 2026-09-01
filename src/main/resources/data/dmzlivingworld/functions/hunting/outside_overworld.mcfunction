execute if score @s lw_scout_state matches 1.. run tellraw @s [{"text":"[Living World] ","color":"gold"},{"text":"The pursuit cannot follow you into this dimension.","color":"gray","italic":true}]
scoreboard players set @s lw_scout_state 0
scoreboard players set @s lw_scout_timer 0
