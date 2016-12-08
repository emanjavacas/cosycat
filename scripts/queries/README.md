JSON schema of the annotation database. The schema is specified as a nested JSON object
indicating the `type`, the optionality (`required`), and other `values` required by
the particular type.
Possible types are `String`, `Integer`, `Boolean` or `Enum` (one-of type).
The `Enum` type takes an array of possible values which can themselves contain other
schemas.

``` json
{
    "ann": {
	    "key"  : {
		    "type": "String",
	        "required": true
	    },
        "value": {
		    "type": "String",
	        "required": true
	    }
    },
    "span": {
        "type": "Enum",
	    "required": true,
	    "values": [
		    {
			    "type": "token",
                "scope": {
				    "type": "Integer",
	                "required": true
				},
	            "doc": {
				    "type": "Any",
	                "required": false
                }
	        },
	        {
			    "type": "IOB",
	            "scope": {
	                "B": {
					    "type": "Integer",
                        "required": true
	                },
	                "O": {
					    "type": "Integer",
	                    "required": true
                    }
                },
	            "doc": {
				    "type": "Any",
                    "required": false
                }
            }
        ]
    },
	"username": {
			"type": "String",
			"required": true
	},
    "timestamp": {
		"type": "Integer",
        "required": true
    },
    "corpus": {
        "type": "String",
        "required": false
    },
    "query": {
        "type": "String",
        "required": false
    },
    "hit-id": {
        "type": "Any",
        "required": false
    }
}
```
