const Discord = require('discord.js');
const Commando = require('discord.js-commando');
const client = new Discord.Client(undefined);
const time = new Date();
const fs = require('fs');
const playerJs = require('./utils/player.js')
const player = new playerJs()

console.log(__dirname)