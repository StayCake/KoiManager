import configparser

config = configparser.ConfigParser()
config.read('main.ini')

token = config['DATA']['token'].strip()
prefix = config['DATA']['prefix'].strip()
