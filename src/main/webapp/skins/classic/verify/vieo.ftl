
<html>
    <body>
        <div id="youkuplayer"style="width:730px;height:482px"></div>
        <script type="text/javascript" src="//player.youku.com/jsapi"></script>

		<script type="text/javascript">
		
		
			var vieoId='${vieoId}';
			var player = new YKU.Player('youkuplayer',{
			
				styleid: '0',
				
				client_id: '48bdfa0ee74f5930',
				
				vid: vieoId,
				
				newPlayer: true
			});
		
		</script>
    </body>
</html>
